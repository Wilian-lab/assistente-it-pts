package com.wlilan.backend_assistent.it.it.usecases;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.assistant.AssistantCacheRepository;
import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.assistant.AssistantDocumentBlockRepository;
import com.wlilan.backend_assistent.it.it.repository.ItRepository;

import jakarta.transaction.Transactional;

@Service
public class DeleteItUseCase {

  private final ItRepository itRepository;
  private final AssistantDocumentBlockRepository assistantDocumentBlockRepository;
  private final AssistantCacheRepository assistantCacheRepository;

  public DeleteItUseCase(
      ItRepository itRepository,
      AssistantDocumentBlockRepository assistantDocumentBlockRepository,
      AssistantCacheRepository assistantCacheRepository) {
    this.itRepository = itRepository;
    this.assistantDocumentBlockRepository = assistantDocumentBlockRepository;
    this.assistantCacheRepository = assistantCacheRepository;
  }

  @Transactional
  public void execute(UUID id, String setorAtivo) {
    var entity = this.itRepository.findByIdAndSetor(id, SetorSupport.normalize(setorAtivo))
        .orElseThrow(() -> new RuntimeException("IT nao encontrada"));

    var fileUrl = entity.getFileUrl();
    if (fileUrl != null && !fileUrl.isBlank()) {
      try {
        Files.deleteIfExists(Path.of(fileUrl));
      } catch (Exception exception) {
        throw new RuntimeException("Falha ao excluir arquivo PDF da IT.");
      }
    }

    this.assistantCacheRepository.deleteByItId(entity.getId());
    this.assistantDocumentBlockRepository.deleteByItId(entity.getId());
    this.itRepository.delete(entity);
  }
}

