package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.HistoricoAlteracao;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoricoAlteracaoRepository extends JpaRepository<HistoricoAlteracao, UUID> {

    List<HistoricoAlteracao> findByEntidadeAndEntidadeIdOrderByOcorridoEmDesc(String entidade, String entidadeId);

    @Query("select h from HistoricoAlteracao h where (:usuarioId is null or h.usuarioId = :usuarioId)"
            + " and (:entidade is null or h.entidade = :entidade) and (:acao is null or h.acao = :acao)"
            + " and (:entidadeId is null or h.entidadeId = :entidadeId)"
            + " and (:de is null or h.ocorridoEm >= :de) and (:ate is null or h.ocorridoEm < :ate)")
    Page<HistoricoAlteracao> pesquisar(@Param("usuarioId") String usuarioId, @Param("entidade") String entidade,
                                       @Param("entidadeId") String entidadeId, @Param("acao") String acao,
                                       @Param("de") Instant de, @Param("ate") Instant ate, Pageable pageable);
}
