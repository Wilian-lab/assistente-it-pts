package com.wlilan.backend_assistent.usuario.useCases;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.wlilan.backend_assistent.DTO.ChangePasswordDTO;
import com.wlilan.backend_assistent.DTO.UpdateProfileDTO;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;

@Service
public class ProfileUseCase {

  private static final long MAX_PROFILE_IMAGE_SIZE = 2 * 1024 * 1024;

  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;

  public ProfileUseCase(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
    this.usuarioRepository = usuarioRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public UsuarioEntity updateProfile(UUID userId, UpdateProfileDTO payload) {
    var user = findUser(userId);
    user.setName(payload.getName().trim());
    return this.usuarioRepository.save(user);
  }

  @Transactional
  public void changePassword(UUID userId, ChangePasswordDTO payload) {
    var user = findUser(userId);
    if (!this.passwordEncoder.matches(payload.getCurrentPassword(), user.getPassword())) {
      throw new IllegalArgumentException("A senha atual informada nao confere.");
    }
    if (this.passwordEncoder.matches(payload.getNewPassword(), user.getPassword())) {
      throw new IllegalArgumentException("A nova senha deve ser diferente da senha atual.");
    }
    user.setPassword(this.passwordEncoder.encode(payload.getNewPassword()));
    this.usuarioRepository.save(user);
  }

  @Transactional
  public UsuarioEntity updateProfileImage(UUID userId, MultipartFile file) {
    var user = findUser(userId);
    validateProfileImage(file);
    try {
      user.setProfileImageData(file.getBytes());
    } catch (IOException exception) {
      throw new IllegalStateException("Nao foi possivel processar a foto de perfil.");
    }
    user.setProfileImageContentType(file.getContentType());
    return this.usuarioRepository.save(user);
  }

  @Transactional
  public UsuarioEntity removeProfileImage(UUID userId) {
    var user = findUser(userId);
    user.setProfileImageData(null);
    user.setProfileImageContentType(null);
    return this.usuarioRepository.save(user);
  }

  private UsuarioEntity findUser(UUID userId) {
    return this.usuarioRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado."));
  }

  private void validateProfileImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Selecione uma imagem para a foto de perfil.");
    }

    var contentType = String.valueOf(file.getContentType() == null ? "" : file.getContentType()).trim().toLowerCase();
    if (!contentType.startsWith("image/")) {
      throw new IllegalArgumentException("Envie uma imagem valida para a foto de perfil.");
    }

    if (file.getSize() > MAX_PROFILE_IMAGE_SIZE) {
      throw new IllegalArgumentException("A foto de perfil deve ter no maximo 2 MB.");
    }
  }
}
