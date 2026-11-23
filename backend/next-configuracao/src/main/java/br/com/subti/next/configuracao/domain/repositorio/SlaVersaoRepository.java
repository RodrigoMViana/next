package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.SlaVersao;
import br.com.subti.next.configuracao.domain.regra.SlaCicloVida.SituacaoVersao;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaVersaoRepository extends JpaRepository<SlaVersao, UUID> {

    List<SlaVersao> findBySlaIdOrderByNumeroDesc(UUID slaId);

    List<SlaVersao> findBySlaIdAndSituacao(UUID slaId, SituacaoVersao situacao);

    List<SlaVersao> findBySituacao(SituacaoVersao situacao);
}
