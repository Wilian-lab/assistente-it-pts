package com.wlilan.backend_assistent.usuario;

import java.time.LocalDate;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.validator.constraints.Length;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wlilan.backend_assistent.Security.SetorSupport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Basic;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Entity
@Table(name = "usuario")
public class UsuarioEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank(message = "Nome e obrigatorio")
  @Pattern(regexp = "^[\\p{L}]+\\s+[\\p{L}]+.*$", message = "Informe nome e sobrenome")
  private String name;

  @NotBlank(message = "Email e obrigatorio")
  @Email(message = "Informe um email valido")
  private String email;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @NotBlank(message = "Senha e obrigatoria")
  @Length(min = 8, message = "A senha deve conter no minimo 8 caracteres")
  private String password;

  @JsonIgnore
  @Column(name = "recovery_code_hash")
  private String recoveryCodeHash;

  @Enumerated(EnumType.STRING)
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Role role;

  private String cargo;

  @Column(name = "setor")
  private String setor;

  @Column(name = "setores")
  private String setores;

  @JsonIgnore
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "usuario_setor",
      joinColumns = @JoinColumn(name = "usuario_id"),
      inverseJoinColumns = @JoinColumn(name = "setor_id"))
  private Set<SetorEntity> setoresRelacionados = new LinkedHashSet<>();

  @Transient
  private String setorAtivo;

  @JsonIgnore
  @Basic(fetch = FetchType.LAZY)
  @Column(name = "profile_image_data", columnDefinition = "bytea")
  private byte[] profileImageData;

  @JsonIgnore
  @Column(name = "profile_image_content_type")
  private String profileImageContentType;

  @Transient
  private String profileImageUrl;

  private String lastTrainedIt;

  private String trainingStatus;

  private LocalDate lastTrainingDate;

  private Integer retrainingIntervalDays;

  private LocalDate nextTrainingDate;

  public String getSetor() {
    var setorPrincipal = this.getSetorCodes().stream().findFirst().orElse(SetorSupport.parseSetor(this.setor));
    this.setor = setorPrincipal;
    return setorPrincipal;
  }

  public String getSetores() {
    var joined = String.join(",", this.getSetorCodes());
    this.setores = joined;
    return joined;
  }

  public void setSetor(String setor) {
    this.setor = SetorSupport.parseSetor(setor);
  }

  public void setSetores(String setores) {
    this.setores = String.join(",", SetorSupport.parseSetores(setores));
  }

  public void setSetoresRelacionados(Collection<SetorEntity> setoresRelacionados) {
    this.setoresRelacionados = new LinkedHashSet<>(setoresRelacionados == null ? Set.of() : setoresRelacionados);
    this.syncLegacySetorFields();
  }

  public Set<String> getSetorCodes() {
    if (this.setoresRelacionados != null && !this.setoresRelacionados.isEmpty()) {
      return this.setoresRelacionados.stream()
          .map(SetorEntity::getCodigo)
          .map(SetorSupport::normalize)
          .filter(value -> !value.isBlank())
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    var fromLegacy = new LinkedHashSet<>(SetorSupport.parseSetores(this.setores));
    if (fromLegacy.isEmpty()) {
      fromLegacy.addAll(SetorSupport.parseSetores(this.setor));
    }
    return fromLegacy;
  }

  public void syncLegacySetorFields() {
    var codes = this.getSetorCodes();
    this.setores = String.join(",", codes);
    this.setor = codes.stream().findFirst().orElse("");
  }

  public boolean hasProfileImage() {
    if (this.profileImageContentType != null && !this.profileImageContentType.isBlank()) {
      return true;
    }
    return this.profileImageData != null && this.profileImageData.length > 0;
  }

  @JsonIgnore
  public String getProfileImagePreviewBase64() {
    if (!hasProfileImage()) {
      return null;
    }
    var contentType = String.valueOf(this.profileImageContentType == null ? "image/png" : this.profileImageContentType).trim();
    return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(this.profileImageData);
  }
}
