package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.Servico;
import br.com.subti.next.configuracao.domain.model.Situacao;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicoRepository extends JpaRepository<Servico, UUID> {

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, UUID id);

    long countByUnidadeMedidaIdAndSituacao(UUID unidadeId, Situacao situacao);

    @Query("select s from Servico s where (:situacao is null or s.situacao = :situacao) and (:tipo is null or s.tipo = :tipo)"
            + " and (:termo is null or lower(s.codigo) like :termo escape '\\' or lower(s.nome) like :termo escape '\\')")
    Page<Servico> pesquisar(@Param("termo") String termo, @Param("situacao") Situacao situacao,
                            @Param("tipo") Servico.Tipo tipo, Pageable pageable);
}
