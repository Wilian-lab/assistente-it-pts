package com.wlilan.backend_assistent.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.assistant.dto.AssistantAskRequest;
import com.wlilan.backend_assistent.assistant.dto.AssistantBenchmarkQuestionResult;
import com.wlilan.backend_assistent.assistant.dto.AssistantBenchmarkRequest;
import com.wlilan.backend_assistent.assistant.dto.AssistantBenchmarkResponse;
import com.wlilan.backend_assistent.assistant.dto.AssistantBenchmarkRunResult;
import com.wlilan.backend_assistent.it.it.usecases.GetItByIdUseCase;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;

@Service
public class AssistantBenchmarkService {

  private static final List<String> DEFAULT_MODELS = List.of(
      "openai/gpt-oss-120b:free",
      "meta-llama/llama-3.3-70b-instruct:free",
      "mistralai/mistral-small-3.1-24b-instruct:free");

  private final AssistantService assistantService;
  private final AssistantOpenRouterClient assistantOpenRouterClient;
  private final GetItByIdUseCase getItByIdUseCase;

  public AssistantBenchmarkService(
      AssistantService assistantService,
      AssistantOpenRouterClient assistantOpenRouterClient,
      GetItByIdUseCase getItByIdUseCase) {
    this.assistantService = assistantService;
    this.assistantOpenRouterClient = assistantOpenRouterClient;
    this.getItByIdUseCase = getItByIdUseCase;
  }

  public AssistantBenchmarkResponse run(AssistantBenchmarkRequest request, UsuarioEntity usuario) {
    var setorAtivo = firstNonBlank(request.setorAtivo(), usuario.getSetorAtivo(), usuario.getSetor());
    var selectedIt = this.getItByIdUseCase.execute(java.util.UUID.fromString(request.itId().trim()), setorAtivo);
    var models = resolveModels(request.models());
    var questionResults = new ArrayList<AssistantBenchmarkQuestionResult>();

    for (var question : request.questions()) {
      if (!hasText(question)) {
        continue;
      }

      var runResults = new ArrayList<AssistantBenchmarkRunResult>();
      for (var model : models) {
        var startedAt = System.nanoTime();
        try {
          var response = this.assistantService.ask(
              new AssistantAskRequest(
                  request.itId(),
                  question.trim(),
                  selectedIt.getDocumento(),
                  selectedIt.getTitulo(),
                  selectedIt.getFileUrl(),
                  setorAtivo,
                  null,
                  null,
                  null,
                  List.of()),
              usuario,
              model,
              true);
          var elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
          runResults.add(AssistantBenchmarkRunResult.builder()
              .model(model)
              .elapsedMs(elapsedMs)
              .sourceType(response.sourceType())
              .answer(response.message())
              .metadata(response.metadata())
              .error(null)
              .build());
        } catch (Exception exception) {
          var elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
          runResults.add(AssistantBenchmarkRunResult.builder()
              .model(model)
              .elapsedMs(elapsedMs)
              .sourceType("benchmark_error")
              .answer("")
              .metadata(java.util.Map.of(
                  "configuredPrimaryModel", firstNonBlank(this.assistantOpenRouterClient.getPrimaryModel(), "-"),
                  "configuredFallbackModel", firstNonBlank(this.assistantOpenRouterClient.getFallbackModel(), "-")))
              .error(firstNonBlank(exception.getMessage(), "Falha ao executar benchmark para o modelo solicitado."))
              .build());
        }
      }

      questionResults.add(AssistantBenchmarkQuestionResult.builder()
          .question(question.trim())
          .runs(runResults)
          .build());
    }

    return AssistantBenchmarkResponse.builder()
        .itId(String.valueOf(selectedIt.getId()))
        .documento(selectedIt.getDocumento())
        .titulo(firstNonBlank(selectedIt.getTitulo(), selectedIt.getDocumento()))
        .models(models)
        .questions(questionResults)
        .build();
  }

  private List<String> resolveModels(List<String> requestedModels) {
    var models = requestedModels == null || requestedModels.isEmpty() ? DEFAULT_MODELS : requestedModels;
    return models.stream()
        .map(model -> firstNonBlank(model, "").trim())
        .filter(this::hasText)
        .map(model -> model.toLowerCase(Locale.ROOT))
        .distinct()
        .limit(5)
        .toList();
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (hasText(value)) {
        return value.trim();
      }
    }
    return "";
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }
}
