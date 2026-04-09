package com.wlilan.backend_assistent.exeptions;

import java.time.LocalDateTime;

public record ErrorMessageDTO(
    String message,
    String field,
    int status,
    LocalDateTime timestamp) {
}
