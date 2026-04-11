package com.wlilan.backend_assistent.pts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PtsFileRepository extends JpaRepository<PtsFileEntity, UUID> {

  Optional<PtsFileEntity> findBySetor(String setor);

  List<PtsFileEntity> findAllByOrderBySetorAsc();

  void deleteBySetor(String setor);
}
