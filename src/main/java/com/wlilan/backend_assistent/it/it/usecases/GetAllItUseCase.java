package com.wlilan.backend_assistent.it.it.usecases;

import java.util.List;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.it.ItEntity;
import com.wlilan.backend_assistent.it.it.repository.ItRepository;

@Service
public class GetAllItUseCase {

  private final ItRepository itRepository;

  public GetAllItUseCase(ItRepository itRepository) {
    this.itRepository = itRepository;
  }

  public List<ItEntity> execute(String setorAtivo) {
    return this.itRepository.findAllBySetorOrderByDocumentoAsc(SetorSupport.normalize(setorAtivo));
  }
}



