package com.wlilan.backend_assistent.DTO;

import com.wlilan.backend_assistent.usuario.UsuarioEntity;

public record TokenResponseDTO(
    String accessToken,
    long expiresIn,
    UsuarioEntity user) {
}
