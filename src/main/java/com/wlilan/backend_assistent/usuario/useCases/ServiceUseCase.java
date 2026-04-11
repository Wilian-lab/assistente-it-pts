package com.wlilan.backend_assistent.usuario.useCases;

import java.util.LinkedHashSet;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.DTO.AdminCreateUserDTO;
import com.wlilan.backend_assistent.DTO.UserTrainingDTO;
import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.usuario.Cargo;
import com.wlilan.backend_assistent.exeptions.UserFoundException;
import com.wlilan.backend_assistent.usuario.Role;
import com.wlilan.backend_assistent.usuario.SetorEntity;
import com.wlilan.backend_assistent.usuario.SetorRepository;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;

@Service
public class ServiceUseCase {

  private final UsuarioRepository usuarioRepository;
  private final SetorRepository setorRepository;
  private final PasswordEncoder passwordEncoder;

  public ServiceUseCase(UsuarioRepository usuarioRepository, SetorRepository setorRepository, PasswordEncoder passwordEncoder) {
    this.usuarioRepository = usuarioRepository;
    this.setorRepository = setorRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public UsuarioEntity execute(UsuarioEntity usuario) {
    return this.createUser(usuario, Role.USER, usuario.getCargo());
  }

  public UsuarioEntity executeAdminCreate(AdminCreateUserDTO userDTO) {
    var usuario = new UsuarioEntity();
    usuario.setName(userDTO.getName());
    usuario.setEmail(userDTO.getEmail());
    usuario.setPassword(userDTO.getPassword());
    usuario.setSetores(userDTO.getSetores());
    return this.createUser(usuario, parseRole(userDTO.getRole()), userDTO.getCargo());
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

    var requestedSetores = new LinkedHashSet<>(SetorSupport.parseSetores(usuario.getSetores()));
    if (requestedSetores.isEmpty()) {
      requestedSetores.addAll(SetorSupport.parseSetores(usuario.getSetor()));
    }
    if (requestedSetores.isEmpty()) {
      throw new IllegalArgumentException("Setor e obrigatorio");
    }
    if ((role == null || role == Role.USER) && requestedSetores.size() > 1) {
      throw new IllegalArgumentException("Usuario comum pode pertencer a apenas um setor");
    }

    usuario.setSetoresRelacionados(
        requestedSetores.stream()
            .map(this::findOrCreateSetor)
            .toList());
    usuario.syncLegacySetorFields();
    return this.usuarioRepository.save(usuario);
  }

  public Iterable<UsuarioEntity> getAll() {
    return this.usuarioRepository.findAll();
  }

  public Iterable<UsuarioEntity> getAllBySetor(String setorAtivo) {
    var normalizedSetor = SetorSupport.normalize(setorAtivo);
    return this.usuarioRepository.findAllBySetorCodigoOrderByNameAsc(normalizedSetor).stream()
        .peek(usuario -> usuario.setSetorAtivo(normalizedSetor))
        .toList();
  }

  public void deleteById(UUID userId, String setorAtivo) {
    var user = this.usuarioRepository.findByIdAndSetorCodigo(userId, SetorSupport.normalize(setorAtivo))
        .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));
    this.usuarioRepository.delete(user);
  }

  public UsuarioEntity updateTraining(UUID userId, UserTrainingDTO trainingDTO, String setorAtivo) {
    var user = this.usuarioRepository.findByIdAndSetorCodigo(userId, SetorSupport.normalize(setorAtivo))
        .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));

    user.setLastTrainedIt(trainingDTO.getLastTrainedIt().trim());
    user.setLastTrainingDate(trainingDTO.getLastTrainingDate());
    user.setRetrainingIntervalDays(trainingDTO.getRetrainingIntervalDays());
    user.setNextTrainingDate(
        trainingDTO.getLastTrainingDate().plusDays(trainingDTO.getRetrainingIntervalDays()));

    return this.usuarioRepository.save(user);
  }

  private Role parseRole(String rawRole) {
    try {
      return Role.valueOf(String.valueOf(rawRole).trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return Role.USER;
    }
  }

  private SetorEntity findOrCreateSetor(String codigo) {
    var normalizedCodigo = SetorSupport.normalize(codigo);
    return this.setorRepository.findByCodigo(normalizedCodigo)
        .orElseGet(() -> {
          var setor = new SetorEntity();
          setor.setCodigo(normalizedCodigo);
          return this.setorRepository.save(setor);
        });
  }
}
