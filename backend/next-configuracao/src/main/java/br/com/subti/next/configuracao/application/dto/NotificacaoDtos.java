package br.com.subti.next.configuracao.application.dto;

import br.com.subti.next.configuracao.domain.model.NotificacaoConfig;
import br.com.subti.next.configuracao.domain.model.Situacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** DTOs de notificação (N06). */
public final class NotificacaoDtos {

    private NotificacaoDtos() {
    }

    /** Evento do catálogo, com as variáveis que o modelo pode usar. */
    public record EventoCatalogo(String evento, String modulo, String descricao, List<String> variaveis,
                                 boolean exigeLimiar) {
    }

    public record NotificacaoEntrada(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Size(max = 120) String evento,
            boolean canalApp,
            boolean canalEmail,
            @NotEmpty Set<String> perfis,
            @NotBlank @Size(max = 200) String assunto,
            @NotBlank @Size(max = 4000) String modelo,
            Integer limiarPercentual) {
    }

    public record NotificacaoSaida(UUID id, String nome, String evento, boolean canalApp, boolean canalEmail,
                                   Set<String> perfis, String assunto, String modelo, Integer limiarPercentual,
                                   Situacao situacao, String justificativaSituacao, long version,
                                   Instant createdAt, Instant updatedAt) {
        public static NotificacaoSaida de(NotificacaoConfig n) {
            return new NotificacaoSaida(n.getId(), n.getNome(), n.getEvento(), n.isCanalApp(), n.isCanalEmail(),
                    n.perfisSet(), n.getAssunto(), n.getModelo(), n.getLimiarPercentual(), n.getSituacao(),
                    n.getJustificativaSituacao(), n.versao(), n.getCreatedAt(), n.getUpdatedAt());
        }
    }

    public record PreVisualizacaoEntrada(@NotBlank String evento, @NotBlank String assunto, @NotBlank String modelo) {
    }

    public record PreVisualizacaoSaida(String assunto, String mensagem, Map<String, String> exemplo) {
    }
}
