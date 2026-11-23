package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.ContratadaAlteracao;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratadaAlteracaoRepository extends JpaRepository<ContratadaAlteracao, UUID> {

    List<ContratadaAlteracao> findByContratadaIdOrderByDataAlteracaoDescCreatedAtDesc(UUID contratadaId);
}
