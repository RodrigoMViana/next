package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.FonteRecurso;
import br.com.subti.next.configuracao.domain.model.Situacao;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FonteRecursoRepository extends JpaRepository<FonteRecurso, UUID> {

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, UUID id);

    @Query("select f from FonteRecurso f where (:situacao is null or f.situacao = :situacao) and (:termo is null"
            + " or lower(f.codigo) like :termo escape '\\' or lower(f.nome) like :termo escape '\\')")
    Page<FonteRecurso> pesquisar(@Param("termo") String termo, @Param("situacao") Situacao situacao, Pageable pageable);
}
