package com.wlilan.backend_assistent.usuario.useCases;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wlilan.backend_assistent.DTO.AdminCreateUserDTO;
import com.wlilan.backend_assistent.DTO.AdminCreateUserResponseDTO;
import com.wlilan.backend_assistent.DTO.GeneratedRecoveryCodeResponseDTO;
import com.wlilan.backend_assistent.DTO.SetorOptionDTO;
import com.wlilan.backend_assistent.DTO.UserTrainingDTO;
import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.usuario.Role;
import com.wlilan.backend_assistent.usuario.SetorEntity;
import com.wlilan.backend_assistent.usuario.SetorRepository;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;
import com.wlilan.backend_assistent.usuario.useCases.passwordreset.PasswordResetMailService;
import com.wlilan.backend_assistent.it.UserFoundException;

@Service
public class ServiceUseCase {
  private static final String RECOVERY_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private final UsuarioRepository usuarioRepository;
  private final SetorRepository setorRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordResetMailService passwordResetMailService;
  private final List<LinkedHashSet<String>> linkedSectorGroups;
  private final Random random = new Random();

  public ServiceUseCase(
      UsuarioRepository usuarioRepository,
      SetorRepository setorRepository,
      PasswordEncoder passwordEncoder,
      PasswordResetMailService passwordResetMailService,
      @Value("${app.setor-linked-groups:}") String linkedSectorGroupsRaw) {
    this.usuarioRepository = usuarioRepository;
    this.setorRepository = setorRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordResetMailService = passwordResetMailService;
    this.linkedSectorGroups = parseLinkedSectorGroups(linkedSectorGroupsRaw);
  }

  public UsuarioEntity execute(UsuarioEntity usuario) {
    return this.createUser(usuario, Role.USER, usuario.getCargo());
  }

  @Transactional
  public AdminCreateUserResponseDTO executeAdminCreate(AdminCreateUserDTO userDTO) {
    var usuario = new UsuarioEntity();
    usuario.setName(userDTO.getName());
    usuario.setEmail(userDTO.getEmail());
    usuario.setPassword(userDTO.getPassword());
    usuario.setSetores(userDTO.getSetores());
    return this.createUserWithGeneratedRecoveryCode(usuario, parseRole(userDTO.getRole()), userDTO.getCargo(), null);
  }

  @Transactional
  public AdminCreateUserResponseDTO executeAdminCreate(AdminCreateUserDTO userDTO, UsuarioEntity actor) {
    var usuario = new UsuarioEntity();
    usuario.setName(userDTO.getName());
    usuario.setEmail(userDTO.getEmail());
    usuario.setPassword(userDTO.getPassword());
    usuario.setSetores(userDTO.getSetores());
    return this.createUserWithGeneratedRecoveryCode(usuario, parseRole(userDTO.getRole()), userDTO.getCargo(), actor);
  }

  private AdminCreateUserResponseDTO createUserWithGeneratedRecoveryCode(
      UsuarioEntity usuario,
      Role role,
      String cargo,
      UsuarioEntity actor) {
    var generatedRecoveryCode = generateRecoveryCode();
    usuario.setRecoveryCodeHash(encodeRecoveryCode(generatedRecoveryCode));
    var createdUser = this.createUser(usuario, role, cargo, actor);
    this.passwordResetMailService.sendRecoveryCode(createdUser, generatedRecoveryCode, createdUser.getSetor(), false);
    return new AdminCreateUserResponseDTO(
        "Usuario criado com sucesso. Codigo de recuperacao enviado por email.",
        generatedRecoveryCode,
        true,
        createdUser);
  }

  private UsuarioEntity createUser(UsuarioEntity usuario, Role role, String cargo) {
    return createUser(usuario, role, cargo, null);
  }

  private UsuarioEntity createUser(UsuarioEntity usuario, Role role, String cargo, UsuarioEntity actor) {
    this.usuarioRepository.findByNameOrEmail(usuario.getName(), usuario.getEmail())
        .ifPresent(existingUsuario -> {
          throw new UserFoundException();
        });

    var encodedPassword = this.passwordEncoder.encode(usuario.getPassword());
    usuario.setPassword(encodedPassword);
    usuario.setRole(role != null ? role : Role.USER);
    usuario.setCargo(normalizeCargo(cargo));

    var requestedSetores = new LinkedHashSet<>(SetorSupport.parseSetores(usuario.getSetores()));
    if (requestedSetores.isEmpty()) {
      requestedSetores.addAll(SetorSupport.parseSetores(usuario.getSetor()));
    }
    if (requestedSetores.isEmpty()) {
      throw new IllegalArgumentException("Setor e obrigatorio");
    }

    var safeRole = role != null ? role : Role.USER;
    validateActorCanCreateRequestedRole(actor, safeRole, requestedSetores);

    if (safeRole != Role.SUPER_ADMIN && requestedSetores.size() > 1) {
      throw new IllegalArgumentException("Usuario comum pode pertencer a apenas um setor");
    }

    usuario.setRole(safeRole);
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

  public Iterable<UsuarioEntity> getVisibleUsers(UsuarioEntity actor, String setorFiltro) {
    if (isSuperAdmin(actor)) {
      var normalizedSetor = SetorSupport.normalize(setorFiltro);
      if (normalizedSetor.isBlank()) {
        return this.usuarioRepository.findAllByOrderByNameAsc();
      }
      return this.getAllBySetor(normalizedSetor);
    }

    var allowedSetores = expandAllowedSetores(actor.getSetorCodes());
    if (allowedSetores.isEmpty()) {
      var setorAtivo = SetorSupport.normalize(actor.getSetorAtivo());
      if (!setorAtivo.isBlank()) {
        allowedSetores.add(setorAtivo);
      }
    }

    var normalizedFiltro = SetorSupport.normalize(setorFiltro);
    if (!normalizedFiltro.isBlank()) {
      if (!allowedSetores.contains(normalizedFiltro)) {
        throw new IllegalArgumentException("O administrador nao possui acesso ao setor informado.");
      }
      return this.getAllBySetor(normalizedFiltro);
    }

    if (allowedSetores.size() == 1) {
      return this.getAllBySetor(allowedSetores.iterator().next());
    }

    return this.usuarioRepository.findAllBySetorCodigoInOrderByNameAsc(allowedSetores);
  }

  public void deleteById(UUID userId, UsuarioEntity actor) {
    var user = findManagedUser(userId, actor);
    if (!isSuperAdmin(actor) && user.getRole() == Role.ADMIN) {
      throw new IllegalArgumentException("Administrador comum nao pode excluir outro administrador.");
    }
    if (user.getRole() == Role.SUPER_ADMIN) {
      throw new IllegalArgumentException("Nao e permitido excluir o super administrador por este fluxo.");
    }
    this.usuarioRepository.delete(user);
  }

  public UsuarioEntity updateTraining(UUID userId, UserTrainingDTO trainingDTO, String setorAtivo) {
    var user = this.usuarioRepository.findByIdAndSetorCodigo(userId, SetorSupport.normalize(setorAtivo))
        .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));

    user.setLastTrainedIt(trainingDTO.getLastTrainedIt().trim());
    user.setTrainingStatus(normalizeTrainingStatus(trainingDTO.getTrainingStatus()));
    user.setLastTrainingDate(trainingDTO.getLastTrainingDate());
    user.setRetrainingIntervalDays(trainingDTO.getRetrainingIntervalDays());
    user.setNextTrainingDate(
        trainingDTO.getLastTrainingDate().plusDays(trainingDTO.getRetrainingIntervalDays()));

    return this.usuarioRepository.save(user);
  }

  @Transactional
  public UsuarioEntity updateUserSetores(UUID userId, String setoresRaw, UsuarioEntity actor) {
    if (!isSuperAdmin(actor)) {
      throw new IllegalArgumentException("Somente o super administrador pode atualizar setores de usuarios.");
    }

    var user = this.usuarioRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));

    if (user.getRole() == Role.SUPER_ADMIN) {
      throw new IllegalArgumentException("Nao e permitido alterar os setores do super administrador por este fluxo.");
    }

    var requestedSetores = new LinkedHashSet<>(SetorSupport.parseSetores(setoresRaw));
    if (requestedSetores.isEmpty()) {
      throw new IllegalArgumentException("Informe ao menos um setor.");
    }

    if (user.getRole() != Role.ADMIN && requestedSetores.size() > 1) {
      throw new IllegalArgumentException("Usuario comum pode pertencer a apenas um setor.");
    }

    user.setSetoresRelacionados(
        requestedSetores.stream()
            .map(this::findOrCreateSetor)
            .toList());
    user.syncLegacySetorFields();

    var normalizedSetorAtivo = SetorSupport.normalize(user.getSetorAtivo());
    if (!normalizedSetorAtivo.isBlank() && !user.getSetorCodes().contains(normalizedSetorAtivo)) {
      user.setSetorAtivo(user.getSetor());
    }

    return this.usuarioRepository.save(user);
  }

  public List<SetorEntity> getVisibleSetores(UsuarioEntity actor) {
    if (isSuperAdmin(actor)) {
      return this.setorRepository.findAllByOrderByCodigoAsc();
    }
    var allowedSetores = expandAllowedSetores(actor.getSetorCodes());
    if (allowedSetores.isEmpty()) {
      var setorAtivo = SetorSupport.normalize(actor.getSetorAtivo());
      if (!setorAtivo.isBlank()) {
        allowedSetores.add(setorAtivo);
      }
    }
    if (allowedSetores.isEmpty()) {
      return List.of();
    }
    return this.setorRepository.findAllByCodigoIn(allowedSetores).stream()
        .sorted(java.util.Comparator.comparing(SetorEntity::getCodigo))
        .toList();
  }

  public boolean actorCanAccessSetor(UsuarioEntity actor, String setor) {
    var normalizedSetor = SetorSupport.normalize(setor);
    if (normalizedSetor.isBlank() || actor == null) {
      return false;
    }

    if (isSuperAdmin(actor)) {
      return this.setorRepository.findByCodigo(normalizedSetor).isPresent();
    }

    return expandAllowedSetores(actor.getSetorCodes()).contains(normalizedSetor);
  }

  public List<SetorOptionDTO> getLoginSetores() {
    var setores = this.setorRepository.findDistinctAssignedSetores();
    if (setores.isEmpty()) {
      setores = this.setorRepository.findAllByOrderByCodigoAsc();
    }
    return setores.stream()
        .map(setor -> new SetorOptionDTO(setor.getCodigo()))
        .toList();
  }

  public SetorEntity createSetor(String codigo, UsuarioEntity actor) {
    if (!isSuperAdmin(actor)) {
      throw new IllegalArgumentException("Somente o super administrador pode criar novos setores.");
    }
    return this.findOrCreateSetor(codigo);
  }

  @Transactional
  public GeneratedRecoveryCodeResponseDTO updateRecoveryCode(UUID userId, UsuarioEntity actor) {
    var user = findManagedUser(userId, actor);
    if (!isSuperAdmin(actor) && user.getRole() == Role.ADMIN) {
      throw new IllegalArgumentException("Administrador comum nao pode alterar o codigo de recuperacao de outro administrador.");
    }
    var generatedRecoveryCode = generateRecoveryCode();
    user.setRecoveryCodeHash(encodeRecoveryCode(generatedRecoveryCode));
    var updatedUser = this.usuarioRepository.save(user);
    this.passwordResetMailService.sendRecoveryCode(updatedUser, generatedRecoveryCode, updatedUser.getSetor(), true);
    return new GeneratedRecoveryCodeResponseDTO(
        "Codigo de recuperacao atualizado e enviado por email.",
        generatedRecoveryCode,
        true);
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
    if (normalizedCodigo.isBlank()) {
      throw new IllegalArgumentException("Setor e obrigatorio");
    }
    return this.setorRepository.findByCodigo(normalizedCodigo)
        .orElseGet(() -> {
          var setor = new SetorEntity();
          setor.setCodigo(normalizedCodigo);
          return this.setorRepository.save(setor);
        });
  }

  private void validateActorCanCreateRequestedRole(UsuarioEntity actor, Role targetRole, LinkedHashSet<String> requestedSetores) {
    if (actor == null) {
      return;
    }

    if (isSuperAdmin(actor)) {
      if (targetRole == Role.SUPER_ADMIN) {
        throw new IllegalArgumentException("Criacao de outro super administrador nao e permitida por este fluxo.");
      }
      return;
    }

    if (targetRole == Role.ADMIN || targetRole == Role.SUPER_ADMIN) {
      throw new IllegalArgumentException("Administrador comum nao pode criar administradores.");
    }

    var allowedSetores = expandAllowedSetores(actor.getSetorCodes());
    if (allowedSetores.isEmpty()) {
      var setorAtivo = SetorSupport.normalize(actor.getSetorAtivo());
      if (!setorAtivo.isBlank()) {
        allowedSetores.add(setorAtivo);
      }
    }

    if (requestedSetores.size() != 1 || allowedSetores.stream().noneMatch(requestedSetores::contains)) {
      throw new IllegalArgumentException("O administrador so pode cadastrar usuarios nos setores vinculados a ele.");
    }
  }

  private UsuarioEntity findManagedUser(UUID userId, UsuarioEntity actor) {
    if (isSuperAdmin(actor)) {
      return this.usuarioRepository.findById(userId)
          .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));
    }

    return this.usuarioRepository.findByIdAndSetorCodigo(userId, SetorSupport.normalize(actor.getSetorAtivo()))
        .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));
  }

  private boolean isSuperAdmin(UsuarioEntity actor) {
    return actor != null && actor.getRole() == Role.SUPER_ADMIN;
  }

  private String encodeRecoveryCode(String recoveryCode) {
    if (recoveryCode == null || recoveryCode.trim().isBlank()) {
      return null;
    }
    return this.passwordEncoder.encode(recoveryCode.trim());
  }

  private String normalizeCargo(String cargo) {
    return String.valueOf(cargo == null ? "" : cargo).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
  }

  private String normalizeTrainingStatus(String trainingStatus) {
    return String.valueOf(trainingStatus == null ? "" : trainingStatus)
        .trim()
        .toUpperCase(Locale.ROOT)
        .replace(' ', '_');
  }

  private String generateRecoveryCode() {
    return String.format(
        Locale.ROOT,
        "%s-%s-%s",
        randomChunk(4),
        randomChunk(4),
        randomChunk(4));
  }

  private String randomChunk(int size) {
    var builder = new StringBuilder(size);
    for (int index = 0; index < size; index++) {
      var position = this.random.nextInt(RECOVERY_CODE_ALPHABET.length());
      builder.append(RECOVERY_CODE_ALPHABET.charAt(position));
    }
    return builder.toString();
  }

  private List<LinkedHashSet<String>> parseLinkedSectorGroups(String raw) {
    return java.util.Arrays.stream(String.valueOf(raw == null ? "" : raw).split(";"))
        .map(SetorSupport::parseGroupedSetores)
        .map(LinkedHashSet::new)
        .filter(group -> !group.isEmpty())
        .toList();
  }

  private LinkedHashSet<String> expandAllowedSetores(java.util.Collection<String> setorCodes) {
    var expanded = new LinkedHashSet<String>();
    for (var setorCode : setorCodes == null ? List.<String>of() : setorCodes) {
      var normalized = SetorSupport.normalize(setorCode);
      if (normalized.isBlank()) {
        continue;
      }
      expanded.add(normalized);
      for (var group : this.linkedSectorGroups) {
        if (group.contains(normalized)) {
          expanded.addAll(group);
        }
      }
    }
    return expanded;
  }
}
