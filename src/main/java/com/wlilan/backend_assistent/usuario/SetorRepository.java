package com.wlilan.backend_assistent.usuario;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SetorRepository extends JpaRepository<SetorEntity, UUID> {

  Optional<SetorEntity> findByCodigo(String codigo);

  List<SetorEntity> findAllByCodigoIn(Collection<String> codigos);

  List<SetorEntity> findAllByOrderByCodigoAsc();

  @Query("select distinct s from UsuarioEntity u join u.setoresRelacionados s order by s.codigo asc")
  List<SetorEntity> findDistinctAssignedSetores();
}
