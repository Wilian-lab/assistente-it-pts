package com.wlilan.backend_assistent.pts;

import lombok.Builder;

@Builder
public record PtsFileDTO(
    String setor,
    String fileName,
    String path,
    long size,
    String lastModified,
    long recordsCount) {
}
