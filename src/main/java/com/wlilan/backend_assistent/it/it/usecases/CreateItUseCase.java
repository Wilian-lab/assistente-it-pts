package com.wlilan.backend_assistent.it.it.usecases;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.it.UserFoundException;
import com.wlilan.backend_assistent.it.ItEntity;
import com.wlilan.backend_assistent.it.it.repository.ItRepository;

@Service
public class CreateItUseCase {

  private final ItRepository itRepository;

  public CreateItUseCase(ItRepository itRepository) {
    this.itRepository = itRepository;
  }

  public ItEntity execute(ItEntity it, String setorAtivo) {
    var normalizedSetor = SetorSupport.normalize(setorAtivo);
    it.setSetor(normalizedSetor);
    validate(it);

    this.itRepository.findByDocumentoAndRevisaoAndSetor(it.getDocumento(), it.getRevisao(), normalizedSetor)
        .ifPresent(existing -> {
          throw new UserFoundException("Ja existe uma IT cadastrada com este documento e revisao");
        });

    return this.itRepository.save(it);
  }

  private void validate(ItEntity it) {
    if (it == null) {
      throw new IllegalArgumentException("IT nao pode ser nula");
    }

    if (it.getDocumento() == null || it.getDocumento().isBlank()) {
      throw new IllegalArgumentException("Documento e obrigatorio");
    }

    if (it.getRevisao() == null || it.getRevisao().isBlank()) {
      throw new IllegalArgumentException("Revisao e obrigatoria");
    }

    if (it.getStatus() == null || it.getStatus().isBlank()) {
      throw new IllegalArgumentException("Status e obrigatorio");
    }

    if (SetorSupport.normalize(it.getSetor()).isBlank()) {
      throw new IllegalArgumentException("Setor e obrigatorio");
    }

    if (it.getDataPublicacao() == null) {
      throw new IllegalArgumentException("Data de publicacao e obrigatoria");
    }

    if (it.getPaginaAtual() == null || it.getPaginaAtual() < 1) {
      throw new IllegalArgumentException("Pagina atual invalida");
    }

    if (it.getTotalPaginas() == null || it.getTotalPaginas() < 1) {
      throw new IllegalArgumentException("Total de paginas invalido");
    }

    if (it.getPaginaAtual() > it.getTotalPaginas()) {
      throw new IllegalArgumentException("Pagina atual nao pode ser maior que o total de paginas");
    }

    if (it.getPrazoTreinamentoDias() == null || it.getPrazoTreinamentoDias() < 0) {
      throw new IllegalArgumentException("Prazo de treinamento invalido");
    }
  }
}
