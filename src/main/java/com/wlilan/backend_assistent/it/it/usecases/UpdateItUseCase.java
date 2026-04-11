package com.wlilan.backend_assistent.it.it.usecases;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.exeptions.UserFoundException;
import com.wlilan.backend_assistent.it.ItEntity;
import com.wlilan.backend_assistent.it.it.repository.ItRepository;

@Service
public class UpdateItUseCase {

  private final ItRepository itRepository;

  public UpdateItUseCase(ItRepository itRepository) {
    this.itRepository = itRepository;
  }

  public ItEntity execute(UUID id, ItEntity payload, String setorAtivo) {
    var normalizedSetor = SetorSupport.normalize(setorAtivo);
    payload.setSetor(normalizedSetor);
    validate(payload);

    var existing = this.itRepository.findByIdAndSetor(id, normalizedSetor)
        .orElseThrow(() -> new RuntimeException("IT nao encontrada"));

    this.itRepository.findByDocumentoAndRevisaoAndSetorAndIdNot(payload.getDocumento(), payload.getRevisao(), normalizedSetor, id)
        .ifPresent(conflict -> {
          throw new UserFoundException("Ja existe uma IT cadastrada com este documento e revisao");
        });

    existing.setDocumento(payload.getDocumento());
    existing.setRevisao(payload.getRevisao());
    existing.setStatus(payload.getStatus());
    existing.setDataPublicacao(payload.getDataPublicacao());
    existing.setPaginaAtual(payload.getPaginaAtual());
    existing.setTotalPaginas(payload.getTotalPaginas());
    existing.setPrazoTreinamentoDias(payload.getPrazoTreinamentoDias());
    existing.setTitulo(payload.getTitulo());
    existing.setFileUrl(payload.getFileUrl());
    existing.setSetor(normalizedSetor);

    return this.itRepository.save(existing);
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
