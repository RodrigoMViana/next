package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.Contrato;
import br.com.subti.next.configuracao.domain.model.Situacao;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContratoRepository extends JpaRepository<Contrato, UUID> {

    boolean existsByNumeroIgnoreCaseAndIdNot(String numero, UUID id);

    long countByContratadaIdAndSituacao(UUID contratadaId, Situacao situacao);

    List<Contrato> findByContratadaIdAndSituacao(UUID contratadaId, Situacao situacao);

    List<Contrato> findBySituacao(Situacao situacao);

    @Query("select count(c) from Contrato c join c.itens i where i.servico.id = :servicoId and c.situacao = :situacao")
    long contarComServico(@Param("servicoId") UUID servicoId, @Param("situacao") Situacao situacao);

    @Query("select count(c) from Contrato c join c.itens i where i.unidadeMedida.id = :unidadeId and c.situacao = :situacao")
    long contarComUnidade(@Param("unidadeId") UUID unidadeId, @Param("situacao") Situacao situacao);

    @Query("select count(c) from Contrato c join c.fontes f where f.id = :fonteId and c.situacao = :situacao")
    long contarComFonte(@Param("fonteId") UUID fonteId, @Param("situacao") Situacao situacao);

    long countByCalendarioIdAndSituacao(UUID calendarioId, Situacao situacao);

    @Query("select c from Contrato c where (:situacao is null or c.situacao = :situacao)"
            + " and (:contratadaId is null or c.contratada.id = :contratadaId)"
            + " and (:vigenteEm is null or (c.vigenciaInicio <= :vigenteEm and c.vigenciaFim >= :vigenteEm))"
            + " and (:termo is null or lower(c.numero) like :termo escape '\\' or lower(c.objeto) like :termo escape '\\')")
    Page<Contrato> pesquisar(@Param("termo") String termo, @Param("situacao") Situacao situacao,
                             @Param("contratadaId") UUID contratadaId, @Param("vigenteEm") LocalDate vigenteEm,
                             Pageable pageable);

    List<Contrato> findBySistemasSistemaId(UUID sistemaId);
}
