package com.wlilan.backend_assistent.DTO;

import com.wlilan.backend_assistent.usuario.UsuarioEntity;

public record AdminCreateUserResponseDTO(
    String message,
    String recoveryCode,
    boolean emailSent,
    UsuarioEntity user) {
}
