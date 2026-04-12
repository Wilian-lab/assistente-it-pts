package com.wlilan.backend_assistent.usuario;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {

  Optional<UsuarioEntity> findByNameOrEmail(String name, String email);

  Optional<UsuarioEntity> findByEmail(String email);

  long countByRole(Role role);

  List<UsuarioEntity> findAllByOrderByNameAsc();

  @Query("""
      select distinct u
      from UsuarioEntity u
      join u.setoresRelacionados s
      where s.codigo = :setor
      order by u.name asc
      """)
  List<UsuarioEntity> findAllBySetorCodigoOrderByNameAsc(@Param("setor") String setor);

  @Query("""
      select distinct u
      from UsuarioEntity u
      join u.setoresRelacionados s
      where s.codigo in :setores
      order by u.name asc
      """)
  List<UsuarioEntity> findAllBySetorCodigoInOrderByNameAsc(@Param("setores") Collection<String> setores);

  @Query("""
      select distinct u
      from UsuarioEntity u
      join u.setoresRelacionados s
      where u.id = :id
        and s.codigo = :setor
      """)
  Optional<UsuarioEntity> findByIdAndSetorCodigo(@Param("id") UUID id, @Param("setor") String setor);
}
