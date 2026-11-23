package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.Sla;
import br.com.subti.next.configuracao.domain.model.Situacao;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlaRepository extends JpaRepository<Sla, UUID> {

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, UUID id);

    List<Sla> findBySistemasSistemaId(UUID sistemaId);

    List<Sla> findByContratoId(UUID contratoId);

    @Query("select s from Sla s where (:situacao is null or s.situacao = :situacao)"
            + " and (:tipo is null or s.tipoResponsavel = :tipo)"
            + " and (:contratoId is null or s.contrato.id = :contratoId)"
            + " and (:fabrica is null or s.contratadaId = :fabrica)"
            + " and (:termo is null or lower(s.codigo) like :termo escape '\\' or lower(s.nome) like :termo escape '\\')")
    Page<Sla> pesquisar(@Param("termo") String termo, @Param("situacao") Situacao situacao,
                        @Param("tipo") Sla.TipoResponsavel tipo, @Param("contratoId") UUID contratoId,
                        @Param("fabrica") UUID fabrica, Pageable pageable);
}
