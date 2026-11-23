package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.Contratada;
import br.com.subti.next.configuracao.domain.model.Situacao;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContratadaRepository extends JpaRepository<Contratada, UUID> {

    boolean existsByCnpjAndIdNot(String cnpj, UUID id);

    @Query("select c from Contratada c where (:situacao is null or c.situacao = :situacao)"
            + " and (:fabrica is null or c.id = :fabrica)"
            + " and (:termo is null or lower(c.razaoSocial) like :termo escape '\\' or lower(c.nomeFantasia) like :termo escape '\\' or c.cnpj like :cnpj escape '\\')")
    Page<Contratada> pesquisar(@Param("termo") String termo, @Param("cnpj") String cnpj,
                               @Param("situacao") Situacao situacao, @Param("fabrica") UUID fabrica, Pageable pageable);
}
