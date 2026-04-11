package com.wlilan.backend_assistent.assistant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.it.it.repository.ItRepository;

@Service
public class AssistantMaintenanceService {

  private final ItRepository itRepository;
  private final AssistantDocumentBlockRepository assistantDocumentBlockRepository;
  private final AssistantCacheRepository assistantCacheRepository;
  private final AssistantDocumentIndexService assistantDocumentIndexService;

  public AssistantMaintenanceService(
      ItRepository itRepository,
      AssistantDocumentBlockRepository assistantDocumentBlockRepository,
      AssistantCacheRepository assistantCacheRepository,
      AssistantDocumentIndexService assistantDocumentIndexService) {
    this.itRepository = itRepository;
    this.assistantDocumentBlockRepository = assistantDocumentBlockRepository;
    this.assistantCacheRepository = assistantCacheRepository;
    this.assistantDocumentIndexService = assistantDocumentIndexService;
  }

  public Map<String, Integer> rebuildAll() {
    this.assistantCacheRepository.deleteAllInBatch();
    this.assistantDocumentBlockRepository.deleteAllInBatch();

    int indexed = 0;
    int skipped = 0;

    for (var it : this.itRepository.findAll()) {
      var fileUrl = firstNonBlank(it.getFileUrl());
      if (fileUrl.isBlank()) {
        skipped += 1;
        continue;
      }

      var path = Path.of(fileUrl);
      if (!Files.exists(path)) {
        skipped += 1;
        continue;
      }

      this.assistantDocumentIndexService.ensureIndexed(it);
      indexed += 1;
    }

    return Map.of(
        "indexed", indexed,
        "skipped", skipped,
        "blocks", (int) this.assistantDocumentBlockRepository.count());
  }

  private String firstNonBlank(String value) {
    return value == null ? "" : value.trim();
  }
}
