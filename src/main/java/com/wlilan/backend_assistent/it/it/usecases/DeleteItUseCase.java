package com.wlilan.backend_assistent.it.it.usecases;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.it.it.repository.ItRepository;

@Service
public class DeleteItUseCase {

  private final ItRepository itRepository;

  public DeleteItUseCase(ItRepository itRepository) {
    this.itRepository = itRepository;
  }

  public void execute(UUID id) {
    var entity = this.itRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("IT nao encontrada"));

    this.itRepository.delete(entity);
  }
}

