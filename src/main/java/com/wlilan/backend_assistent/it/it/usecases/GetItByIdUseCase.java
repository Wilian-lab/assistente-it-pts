package com.wlilan.backend_assistent.it.it.usecases;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.it.ItEntity;
import com.wlilan.backend_assistent.it.it.repository.ItRepository;

@Service
public class GetItByIdUseCase {

  private final ItRepository itRepository;

  public GetItByIdUseCase(ItRepository itRepository) {
    this.itRepository = itRepository;
  }

  public ItEntity execute(UUID id) {
    return this.itRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("IT nao encontrada"));
  }
}
