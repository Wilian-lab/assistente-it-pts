package com.wlilan.backend_assistent.usuario.useCases;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.DTO.AdminCreateUserDTO;
import com.wlilan.backend_assistent.DTO.UserTrainingDTO;
import com.wlilan.backend_assistent.usuario.Cargo;
import com.wlilan.backend_assistent.exeptions.UserFoundException;
import com.wlilan.backend_assistent.usuario.Role;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;

@Service
public class ServiceUseCase {

  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;

  public ServiceUseCase(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
    this.usuarioRepository = usuarioRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public UsuarioEntity execute(UsuarioEntity usuario) {
    return this.createUser(usuario, Role.USER, usuario.getCargo());
  }

  public UsuarioEntity executeAdminCreate(AdminCreateUserDTO userDTO) {
    var usuario = new UsuarioEntity();
    usuario.setName(userDTO.name());
    usuario.setEmail(userDTO.email());
    usuario.setPassword(userDTO.password());
    return this.createUser(usuario, Role.USER, userDTO.cargo());
  }

  private UsuarioEntity createUser(UsuarioEntity usuario, Role role, Cargo cargo) {
    this.usuarioRepository.findByNameOrEmail(usuario.getName(), usuario.getEmail())
        .ifPresent(existingUsuario -> {
          throw new UserFoundException();
        });

    var encodedPassword = this.passwordEncoder.encode(usuario.getPassword());
    usuario.setPassword(encodedPassword);
    usuario.setRole(role != null ? role : Role.USER);
    usuario.setCargo(cargo);
    return this.usuarioRepository.save(usuario);
  }

  public Iterable<UsuarioEntity> getAll() {
    return this.usuarioRepository.findAll();
  }

  public void deleteById(UUID userId) {
    var user = this.usuarioRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));
    this.usuarioRepository.delete(user);
  }

  public UsuarioEntity updateTraining(UUID userId, UserTrainingDTO trainingDTO) {
    var user = this.usuarioRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));

    user.setLastTrainedIt(trainingDTO.getLastTrainedIt().trim());
    user.setLastTrainingDate(trainingDTO.getLastTrainingDate());
    user.setRetrainingIntervalDays(trainingDTO.getRetrainingIntervalDays());
    user.setNextTrainingDate(
        trainingDTO.getLastTrainingDate().plusDays(trainingDTO.getRetrainingIntervalDays()));

    return this.usuarioRepository.save(user);
  }
}
