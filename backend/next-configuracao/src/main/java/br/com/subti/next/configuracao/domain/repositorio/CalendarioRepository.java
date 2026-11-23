package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.Calendario;
import br.com.subti.next.configuracao.domain.model.Situacao;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarioRepository extends JpaRepository<Calendario, UUID> {

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    @Query("select c from Calendario c where (:situacao is null or c.situacao = :situacao) and (:termo is null"
            + " or lower(c.nome) like :termo escape '\\')")
    Page<Calendario> pesquisar(@Param("termo") String termo, @Param("situacao") Situacao situacao, Pageable pageable);
}
