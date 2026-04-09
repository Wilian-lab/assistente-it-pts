package com.wlilan.backend_assistent.it.it.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wlilan.backend_assistent.it.ItEntity;

public interface ItRepository extends JpaRepository<ItEntity, UUID> {

  Optional<ItEntity> findByDocumentoAndRevisao(String documento, String revisao);

  Optional<ItEntity> findByDocumentoAndRevisaoAndIdNot(String documento, String revisao, UUID id);

}
