package com.wlilan.backend_assistent.pts;

import lombok.Builder;

@Builder
public record PtsFileDTO(
    String setor,
    String fileName,
    long size,
    String lastModified,
    long recordsCount) {
}
