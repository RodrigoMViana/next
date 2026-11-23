package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.NotificacaoConfig;
import br.com.subti.next.configuracao.domain.model.Situacao;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacaoConfigRepository extends JpaRepository<NotificacaoConfig, UUID> {

    @Query("select n from NotificacaoConfig n where (:situacao is null or n.situacao = :situacao)"
            + " and (:evento is null or n.evento = :evento)"
            + " and (:termo is null or lower(n.nome) like :termo escape '\\' or lower(n.evento) like :termo escape '\\')")
    Page<NotificacaoConfig> pesquisar(@Param("termo") String termo, @Param("situacao") Situacao situacao,
                                      @Param("evento") String evento, Pageable pageable);
}
