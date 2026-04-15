package com.wlilan.backend_assistent.assistant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AssistantOpenRouterClient {

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String openRouterUrl;
  private final String openRouterModel;
  private final String openRouterFallbackModel;
  private final List<String> openRouterExtraFallbackModels;
  private final String openRouterApiKey;
  private final String applicationName;

  public AssistantOpenRouterClient(
      ObjectMapper objectMapper,
      @Value("${assistant.openrouter.url:https://openrouter.ai/api/v1/chat/completions}") String openRouterUrl,
      @Value("${assistant.openrouter.model:openrouter/free}") String openRouterModel,
      @Value("${assistant.openrouter.fallback-model:google/gemma-3-27b-it:free}") String openRouterFallbackModel,
      @Value("${assistant.openrouter.extra-fallback-models:openai/gpt-oss-20b:free,mistralai/mistral-small-3.1-24b-instruct:free,openrouter/free}") String openRouterExtraFallbackModels,
      @Value("${assistant.openrouter.api-key:}") String openRouterApiKey,
      @Value("${assistant.openrouter.app-name:Assistente IT}") String applicationName) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    this.openRouterUrl = openRouterUrl;
    this.openRouterModel = openRouterModel;
    this.openRouterFallbackModel = openRouterFallbackModel;
    this.openRouterExtraFallbackModels = Arrays.stream(firstNonBlank(openRouterExtraFallbackModels).split(","))
        .map(String::trim)
        .filter(this::hasText)
        .toList();
    this.openRouterApiKey = openRouterApiKey;
    this.applicationName = applicationName;
  }

  public String chat(List<Map<String, String>> messages) {
    return chat(messages, 0.2d);
  }

  public String chat(List<Map<String, String>> messages, double temperature) {
    return chat(messages, temperature, null, true);
  }

  public String chat(List<Map<String, String>> messages, double temperature, String preferredModel, boolean allowConfiguredFallback) {
    if (!hasText(this.openRouterApiKey)) {
      throw new IllegalArgumentException("Configure assistant.openrouter.api-key para habilitar o assistente externo.");
    }

    var modelsToTry = new LinkedHashSet<String>();
    if (hasText(preferredModel)) {
      modelsToTry.add(preferredModel.trim());
    } else {
      modelsToTry.add(this.openRouterModel);
    }
    if (allowConfiguredFallback && hasText(this.openRouterFallbackModel)) {
      modelsToTry.add(this.openRouterFallbackModel);
    }
    if (allowConfiguredFallback && this.openRouterExtraFallbackModels != null) {
      this.openRouterExtraFallbackModels.forEach(modelsToTry::add);
    }

    String lastError = null;
    for (var model : modelsToTry) {
      try {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", temperature);

        var request = HttpRequest.newBuilder()
            .uri(URI.create(this.openRouterUrl))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + this.openRouterApiKey)
            .header("Content-Type", "application/json")
            .header("X-Title", this.applicationName)
            .POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(payload)))
            .build();

        var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
          var errorMessage = extractOpenRouterError(response.body(), response.statusCode());
          if (response.statusCode() == 429 || response.statusCode() >= 500) {
            lastError = errorMessage;
            continue;
          }
          throw new IllegalArgumentException(errorMessage);
        }

        var root = this.objectMapper.readTree(response.body());
        var content = extractContent(root);
        if (!hasText(content)) {
          lastError = "OpenRouter nao retornou conteudo para a pergunta enviada.";
          continue;
        }
        return content.trim();
      } catch (IllegalArgumentException exception) {
        throw exception;
      } catch (Exception exception) {
        lastError = "Falha ao consultar o provedor externo de IA.";
      }
    }

    throw new IllegalArgumentException(lastError != null
        ? lastError
        : "Testando os Modelos.");
  }

  public String getPrimaryModel() {
    return this.openRouterModel;
  }

  public String getFallbackModel() {
    return this.openRouterFallbackModel;
  }

  private String extractOpenRouterError(String body, int statusCode) {
    try {
      var root = this.objectMapper.readTree(body);
      var message = root.path("error").path("message").asText("");
      if (hasText(message)) {
        return "Falha ao consultar OpenRouter (" + statusCode + "): " + message;
      }
    } catch (Exception ignored) {
      // fall through
    }
    return "Falha ao consultar OpenRouter (" + statusCode + ").";
  }

  private String extractContent(JsonNode root) {
    var contentNode = root.path("choices").path(0).path("message").path("content");
    if (contentNode.isTextual()) {
      return contentNode.asText();
    }
    if (contentNode.isArray()) {
      var parts = new ArrayList<String>();
      for (var item : contentNode) {
        var text = item.path("text").asText("");
        if (hasText(text)) {
          parts.add(text.trim());
        }
      }
      return String.join("\n", parts);
    }
    return "";
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (hasText(value)) {
        return value.trim();
      }
    }
    return "";
  }
}
