package com.wlilan.backend_assistent.assistant.dto;

import lombok.Builder;

@Builder
public record AssistantEvidenceItem(
    Integer passo,
    Integer pagina,
    String entryType,
    Integer sectionNumber,
    String sectionTitle,
    String what,
    String how,
    String care,
    String possibleCauses,
    String actionText) {
}
