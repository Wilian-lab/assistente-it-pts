package com.wlilan.backend_assistent.it.it.usecases;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

public record UploadItPdfCommand(
    MultipartFile file,
    String setor,
    String status,
    UUID existingItId,
    String documento,
    String revisao,
    LocalDateTime dataPublicacao,
    Integer paginaAtual,
    Integer totalPaginas,
    Integer prazoTreinamentoDias) {
}
