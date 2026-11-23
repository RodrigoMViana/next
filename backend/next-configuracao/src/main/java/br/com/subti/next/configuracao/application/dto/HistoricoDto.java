package br.com.subti.next.configuracao.application.dto;

import br.com.subti.next.configuracao.domain.model.HistoricoAlteracao;
import java.time.Instant;
import java.util.UUID;

/** Linha do histórico: o que mudou, quem alterou, quando e o valor anterior (RF16). */
public record HistoricoDto(UUID id, String entidade, String entidadeId, String acao, String campo,
                           String valorAnterior, String valorNovo, String justificativa, String usuarioId,
                           String usuarioNome, Instant ocorridoEm, String correlationId) {

    public static HistoricoDto de(HistoricoAlteracao h) {
        return new HistoricoDto(h.getId(), h.getEntidade(), h.getEntidadeId(), h.getAcao(), h.getCampo(),
                h.getValorAnterior(), h.getValorNovo(), h.getJustificativa(), h.getUsuarioId(), h.getUsuarioNome(),
                h.getOcorridoEm(), h.getCorrelationId());
    }
}
