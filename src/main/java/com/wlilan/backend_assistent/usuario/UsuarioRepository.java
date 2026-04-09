package com.wlilan.backend_assistent.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {

  Optional<UsuarioEntity> findByNameOrEmail(String name, String email);

  Optional<UsuarioEntity> findByEmail(String email);

  long countByRole(Role role); 
}
