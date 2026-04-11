package com.wlilan.backend_assistent.usuario;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SetorRepository extends JpaRepository<SetorEntity, UUID> {

  Optional<SetorEntity> findByCodigo(String codigo);

  List<SetorEntity> findAllByCodigoIn(Collection<String> codigos);
}
