package com.wlilan.backend_assistent.pts;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PtsRecordRepository extends JpaRepository<PtsRecordEntity, UUID> {

  List<PtsRecordEntity> findAllBySetorOrderByProdutoAscItemAscVariavelAsc(String setor);

  List<PtsRecordEntity> findAllBySetorAndProdutoOrderByItemAscVariavelAsc(String setor, String produto);

  List<PtsRecordEntity> findAllBySetorAndProdutoAndItemOrderByVariavelAsc(String setor, String produto, String item);

  List<PtsRecordEntity> findTop20BySetorOrderByProdutoAscItemAscVariavelAsc(String setor);

  void deleteBySetor(String setor);

  long countBySetor(String setor);
}
