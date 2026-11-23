package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.UnidadeMedida;
import br.com.subti.next.configuracao.domain.model.Situacao;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnidadeMedidaRepository extends JpaRepository<UnidadeMedida, UUID> {

    boolean existsBySiglaIgnoreCaseAndIdNot(String sigla, UUID id);

    @Query("select u from UnidadeMedida u where (:situacao is null or u.situacao = :situacao) and (:termo is null"
            + " or lower(u.sigla) like :termo escape '\\' or lower(u.nome) like :termo escape '\\')")
    Page<UnidadeMedida> pesquisar(@Param("termo") String termo, @Param("situacao") Situacao situacao, Pageable pageable);
}
