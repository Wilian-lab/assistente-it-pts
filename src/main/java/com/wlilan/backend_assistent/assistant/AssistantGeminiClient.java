package com.wlilan.backend_assistent.assistant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AssistantGeminiClient {

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String geminiUrl;
  private final String geminiModel;
  private final String geminiApiKey;

  public AssistantGeminiClient(
      ObjectMapper objectMapper,
      @Value("${assistant.gemini.url:https://generativelanguage.googleapis.com/v1beta}") String geminiUrl,
      @Value("${assistant.gemini.model:gemini-2.5-flash-lite}") String geminiModel,
      @Value("${assistant.gemini.api-key:${GEMINI_API_KEY:${GOOGLE_API_KEY:}}}") String geminiApiKey) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    this.geminiUrl = geminiUrl;
    this.geminiModel = geminiModel;
    this.geminiApiKey = geminiApiKey;
  }

  public String chat(List<Map<String, String>> messages, double temperature) {
    if (!hasText(this.geminiApiKey)) {
      throw new IllegalArgumentException("Configure GEMINI_API_KEY para habilitar o provedor Gemini.");
    }

    try {
      var payload = buildPayload(messages, temperature);
      var request = HttpRequest.newBuilder()
          .uri(URI.create(this.geminiUrl + "/models/" + this.geminiModel + ":generateContent"))
          .timeout(Duration.ofSeconds(60))
          .header("x-goog-api-key", this.geminiApiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(this.objectMapper.writeValueAsString(payload)))
          .build();

      var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalArgumentException(extractGeminiError(response.body(), response.statusCode()));
      }

      var root = this.objectMapper.readTree(response.body());
      var content = extractContent(root);
      if (!hasText(content)) {
        throw new IllegalArgumentException("Gemini nao retornou conteudo para a conversa.");
      }
      return content.trim();
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Falha ao consultar a API Gemini.");
    }
  }

  public String getPrimaryModel() {
    return this.geminiModel;
  }

  private Map<String, Object> buildPayload(List<Map<String, String>> messages, double temperature) {
    var payload = new LinkedHashMap<String, Object>();
    var contents = new ArrayList<Map<String, Object>>();
    Map<String, Object> systemInstruction = null;

    for (var message : messages) {
      if (message == null) {
        continue;
      }
      var role = firstNonBlank(message.get("role"));
      var content = firstNonBlank(message.get("content"));
      if (!hasText(content)) {
        continue;
      }

      if ("system".equalsIgnoreCase(role)) {
        systemInstruction = Map.of(
            "parts", List.of(Map.of("text", content)));
        continue;
      }

      contents.add(Map.of(
          "role", "assistant".equalsIgnoreCase(role) || "model".equalsIgnoreCase(role) ? "model" : "user",
          "parts", List.of(Map.of("text", content))));
    }

    if (systemInstruction != null) {
      payload.put("systemInstruction", systemInstruction);
    }
    payload.put("contents", contents);
    payload.put("generationConfig", Map.of(
        "temperature", temperature,
        "candidateCount", 1,
        "maxOutputTokens", 2048));
    return payload;
  }

  private String extractGeminiError(String body, int statusCode) {
    try {
      var root = this.objectMapper.readTree(body);
      var message = root.path("error").path("message").asText("");
      if (hasText(message)) {
        return "Falha ao consultar Gemini (" + statusCode + "): " + message;
      }
    } catch (Exception ignored) {
      // fall through
    }
    return "Falha ao consultar Gemini (" + statusCode + ").";
  }

  private String extractContent(JsonNode root) {
    var parts = root.path("candidates").path(0).path("content").path("parts");
    if (!parts.isArray()) {
      return "";
    }

    var fragments = new ArrayList<String>();
    for (var part : parts) {
      var text = part.path("text").asText("");
      if (hasText(text)) {
        fragments.add(text.trim());
      }
    }
    return String.join("\n", fragments);
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
