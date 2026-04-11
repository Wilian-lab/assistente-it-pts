package com.wlilan.backend_assistent.it.it.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wlilan.backend_assistent.it.ItEntity;

public interface ItRepository extends JpaRepository<ItEntity, UUID> {

  Optional<ItEntity> findByDocumentoAndRevisaoAndSetor(String documento, String revisao, String setor);

  Optional<ItEntity> findByDocumentoAndRevisaoAndSetorAndIdNot(String documento, String revisao, String setor, UUID id);

  Optional<ItEntity> findByFileUrlAndSetor(String fileUrl, String setor);

  java.util.List<ItEntity> findAllBySetorOrderByDocumentoAsc(String setor);

  Optional<ItEntity> findByIdAndSetor(UUID id, String setor);

}
