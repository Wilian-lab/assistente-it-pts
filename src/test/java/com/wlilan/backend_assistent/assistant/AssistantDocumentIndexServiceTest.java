package com.wlilan.backend_assistent.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wlilan.backend_assistent.it.ItEntity;

@ExtendWith(MockitoExtension.class)
class AssistantDocumentIndexServiceTest {

  @Mock
  private AssistantDocumentBlockRepository assistantDocumentBlockRepository;

  @Test
  void shouldCarryStepAcrossContinuationPagesUntilNextStepAppears() throws Exception {
    var pdfPath = createSyntheticPdf(List.of(
        List.of(
            "PASSO  O QUE FAZER  COMO FAZER  CUIDADOS ESPECIAIS",
            "1",
            "Partida das centrifugas de gluten",
            "Verificar condicoes antes da partida.",
            "Ligar painel e aguardar estabilizacao."),
        List.of(
            "Operacao dos lavadores de gluten",
            "Operar com vazao minima estavel.",
            "Monitorar temperatura e ajustar a alimentacao.",
            "Sempre usar a rampa de aquecimento."),
        List.of(
            "2",
            "Parada e limpeza do sistema",
            "Fechar alimentacao e reduzir rotacao.",
            "Aguardar parada completa.")));

    try {
      var it = buildIt(pdfPath);
      var service = new AssistantDocumentIndexService(
          this.assistantDocumentBlockRepository,
          new ObjectMapper(),
          new AssistantIntentDetector(),
          pdfPath.resolveSibling("missing-it-index.json").toString());

      when(this.assistantDocumentBlockRepository.findFirstByItIdOrderByUpdatedAtDesc(it.getId()))
          .thenReturn(Optional.empty());
      when(this.assistantDocumentBlockRepository.saveAll(any()))
          .thenAnswer(invocation -> invocation.getArgument(0));

      service.ensureIndexed(it);

      var savedCaptor = ArgumentCaptor.forClass(List.class);
      verify(this.assistantDocumentBlockRepository).saveAll(savedCaptor.capture());

      @SuppressWarnings("unchecked")
      var savedBlocks = ((List<AssistantDocumentBlockEntity>) savedCaptor.getValue()).stream()
          .filter(block -> "step".equalsIgnoreCase(block.getEntryType()))
          .sorted(Comparator.comparing(AssistantDocumentBlockEntity::getPage))
          .toList();

      assertTrue(savedBlocks.stream().anyMatch(block ->
          Integer.valueOf(1).equals(block.getPage()) && Integer.valueOf(1).equals(block.getStep())));
      assertTrue(savedBlocks.stream().anyMatch(block ->
          Integer.valueOf(2).equals(block.getPage()) && Integer.valueOf(1).equals(block.getStep())));
      assertTrue(savedBlocks.stream().anyMatch(block ->
          Integer.valueOf(3).equals(block.getPage()) && Integer.valueOf(2).equals(block.getStep())));

      var pageTwoBlocks = savedBlocks.stream()
          .filter(block -> Integer.valueOf(2).equals(block.getPage()))
          .toList();

      assertEquals(1, pageTwoBlocks.stream()
          .map(AssistantDocumentBlockEntity::getStep)
          .findFirst()
          .orElseThrow());
      assertTrue(pageTwoBlocks.stream().anyMatch(block ->
          joinBlockText(block).contains("Operar com vazao minima estavel")));
      assertTrue(pageTwoBlocks.stream().anyMatch(block ->
          joinBlockText(block).contains("Sempre usar a rampa de aquecimento")));
    } finally {
      Files.deleteIfExists(pdfPath);
    }
  }

  private ItEntity buildIt(Path pdfPath) throws IOException {
    try (var document = Loader.loadPDF(pdfPath.toFile())) {
      var it = new ItEntity();
      it.setId(UUID.randomUUID());
      it.setDocumento("IT-OPE-TESTE");
      it.setTitulo("IT sintetica multipagina");
      it.setRevisao("1");
      it.setStatus("ATIVA");
      it.setSetor("MOAGEM");
      it.setDataPublicacao(LocalDateTime.now());
      it.setPaginaAtual(1);
      it.setTotalPaginas(document.getNumberOfPages());
      it.setPrazoTreinamentoDias(30);
      it.setFileUrl(pdfPath.toString());
      return it;
    }
  }

  private Path createSyntheticPdf(List<List<String>> pages) throws IOException {
    var pdfPath = Files.createTempFile("assistant-multipage-step-", ".pdf");
    try (var document = new PDDocument()) {
      for (var pageLines : pages) {
        var page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (var content = new PDPageContentStream(document, page)) {
          content.beginText();
          content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
          content.setLeading(16);
          content.newLineAtOffset(48, 780);
          for (var line : pageLines) {
            content.showText(line);
            content.newLine();
          }
          content.endText();
        }
      }
      document.save(pdfPath.toFile());
    }
    return pdfPath;
  }

  private String joinBlockText(AssistantDocumentBlockEntity block) {
    return String.join("\n",
        block.getWhat() == null ? "" : block.getWhat(),
        block.getHow() == null ? "" : block.getHow(),
        block.getCare() == null ? "" : block.getCare());
  }
}
