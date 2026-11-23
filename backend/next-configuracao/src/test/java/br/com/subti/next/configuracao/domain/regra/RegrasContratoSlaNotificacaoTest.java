package br.com.subti.next.configuracao.domain.regra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.subti.next.configuracao.domain.regra.ContratoCalculos.Aditivo;
import br.com.subti.next.configuracao.domain.regra.ContratoCalculos.Item;
import br.com.subti.next.configuracao.domain.regra.SlaCicloVida.SituacaoVersao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegrasContratoSlaNotificacaoTest {

    static LocalDate d(int a, int m, int dia) {
        return LocalDate.of(a, m, dia);
    }

    @Nested
    class Contrato {

        @Test
        void rn01VigenciaFimNaoAnteriorAoInicio() {
            ContratoCalculos.validarVigencia(d(2026, 1, 1), d(2026, 1, 1));
            assertThatThrownBy(() -> ContratoCalculos.validarVigencia(d(2026, 2, 1), d(2026, 1, 31)))
                    .hasMessageContaining("não pode ser anterior");
            assertThatThrownBy(() -> ContratoCalculos.validarVigencia(null, d(2026, 1, 31))).hasMessageContaining("início");
            assertThatThrownBy(() -> ContratoCalculos.validarVigencia(d(2026, 1, 1), null)).hasMessageContaining("fim");
            assertThat(ContratoCalculos.vigenteEm(d(2026, 6, 1), d(2026, 1, 1), d(2026, 12, 31))).isTrue();
            assertThat(ContratoCalculos.vigenteEm(d(2027, 1, 1), d(2026, 1, 1), d(2026, 12, 31))).isFalse();
            assertThat(ContratoCalculos.vigenteEm(d(2025, 12, 31), d(2026, 1, 1), d(2026, 12, 31))).isFalse();
        }

        @Test
        void rn02ValorDoItemEDoContrato() {
            assertThat(ContratoCalculos.valorItem(new BigDecimal("3000"), new BigDecimal("520.00")))
                    .isEqualByComparingTo("1560000.00");
            assertThat(ContratoCalculos.valorItem(new BigDecimal("0.333"), new BigDecimal("10.00")))
                    .isEqualByComparingTo("3.33");
            List<Item> itens = List.of(new Item(new BigDecimal("10"), new BigDecimal("100")),
                    new Item(new BigDecimal("2"), new BigDecimal("50.25")));
            assertThat(ContratoCalculos.valorOriginal(itens)).isEqualByComparingTo("1100.50");
            assertThatThrownBy(() -> ContratoCalculos.valorItem(BigDecimal.ZERO, BigDecimal.TEN)).hasMessageContaining("volume");
            assertThatThrownBy(() -> ContratoCalculos.valorItem(BigDecimal.ONE, new BigDecimal("-1"))).hasMessageContaining("negativo");
        }

        @Test
        void rn05AditivosPreservamOriginaisECalculamEfetivos() {
            List<Item> itens = List.of(new Item(new BigDecimal("10"), new BigDecimal("100")));
            List<Aditivo> aditivos = List.of(
                    new Aditivo(d(2026, 3, 1), null, new BigDecimal("250.00")),
                    new Aditivo(d(2026, 6, 1), d(2027, 6, 30), null),
                    new Aditivo(d(2026, 9, 1), d(2027, 3, 31), new BigDecimal("-100.00")));
            assertThat(ContratoCalculos.valorEfetivo(itens, aditivos)).isEqualByComparingTo("1150.00");
            // o aditivo de prazo mais recente prevalece, mesmo encurtando
            assertThat(ContratoCalculos.vigenciaFimEfetiva(d(2026, 12, 31), aditivos)).isEqualTo(d(2027, 3, 31));
            assertThat(ContratoCalculos.vigenciaFimEfetiva(d(2026, 12, 31), List.of())).isEqualTo(d(2026, 12, 31));
            assertThatThrownBy(() -> ContratoCalculos.valorEfetivo(itens,
                    List.of(new Aditivo(d(2026, 3, 1), null, new BigDecimal("-5000")))))
                    .hasMessageContaining("negativo");
        }

        @Test
        void validaAditivoDePrazo() {
            ContratoCalculos.validarAditivoPrazo(d(2026, 1, 1), d(2026, 12, 31), d(2027, 12, 31));
            assertThatThrownBy(() -> ContratoCalculos.validarAditivoPrazo(d(2026, 1, 1), d(2026, 12, 31), null))
                    .hasMessageContaining("Informe");
            assertThatThrownBy(() -> ContratoCalculos.validarAditivoPrazo(d(2026, 1, 1), d(2026, 12, 31), d(2025, 12, 31)))
                    .hasMessageContaining("anterior ao início");
            assertThatThrownBy(() -> ContratoCalculos.validarAditivoPrazo(d(2026, 1, 1), d(2026, 12, 31), d(2026, 12, 31)))
                    .hasMessageContaining("igual");
        }

        @Test
        void rn06Fiscalizacao() {
            var vigente = new Fiscalizacao.Designacao(d(2026, 1, 1), null);
            var encerrada = new Fiscalizacao.Designacao(d(2025, 1, 1), d(2025, 12, 31));
            assertThat(Fiscalizacao.temFiscalVigente(List.of(vigente, encerrada), d(2026, 5, 1))).isTrue();
            assertThat(Fiscalizacao.temFiscalVigente(List.of(encerrada), d(2026, 5, 1))).isFalse();
            assertThat(Fiscalizacao.fimDaAnterior(d(2026, 1, 1), d(2026, 7, 1))).isEqualTo(d(2026, 6, 30));
            assertThatThrownBy(() -> Fiscalizacao.fimDaAnterior(d(2026, 1, 1), d(2026, 1, 1))).hasMessageContaining("depois");
            assertThatThrownBy(() -> Fiscalizacao.fimDaAnterior(d(2026, 1, 1), null)).hasMessageContaining("Informe");
            Fiscalizacao.validarPeriodo(d(2026, 1, 1), null);
            assertThatThrownBy(() -> Fiscalizacao.validarPeriodo(null, null)).hasMessageContaining("início");
            assertThatThrownBy(() -> Fiscalizacao.validarPeriodo(d(2026, 2, 1), d(2026, 1, 1))).hasMessageContaining("anterior");
        }
    }

    @Nested
    class Sla {

        @Test
        void rn14TransicoesDaVersao() {
            assertThat(SlaCicloVida.podeTransitar(SituacaoVersao.RASCUNHO, SituacaoVersao.EM_AVALIACAO)).isTrue();
            assertThat(SlaCicloVida.podeTransitar(SituacaoVersao.EM_AVALIACAO, SituacaoVersao.VIGENTE)).isTrue();
            assertThat(SlaCicloVida.podeTransitar(SituacaoVersao.EM_AVALIACAO, SituacaoVersao.RASCUNHO)).isTrue();
            assertThat(SlaCicloVida.podeTransitar(SituacaoVersao.RASCUNHO, SituacaoVersao.VIGENTE)).isFalse();
            assertThat(SlaCicloVida.podeTransitar(SituacaoVersao.ENCERRADA, SituacaoVersao.VIGENTE)).isFalse();
            assertThatThrownBy(() -> SlaCicloVida.exigirTransicao(SituacaoVersao.VIGENTE, SituacaoVersao.RASCUNHO))
                    .hasMessage("A versão está vigente e não pode passar para em rascunho.");
            SlaCicloVida.exigirTransicao(SituacaoVersao.VIGENTE, SituacaoVersao.ENCERRADA);
            assertThat(SlaCicloVida.editavel(SituacaoVersao.RASCUNHO)).isTrue();
            assertThat(SlaCicloVida.editavel(SituacaoVersao.EM_AVALIACAO)).isFalse();
            assertThat(SlaCicloVida.rotulo(SituacaoVersao.ENCERRADA)).isEqualTo("encerrada");
        }

        @Test
        void rn14VigenciaNaoRetroativa() {
            LocalDate hoje = d(2026, 11, 23);
            SlaCicloVida.validarInicioVigencia(hoje, hoje, null);
            SlaCicloVida.validarInicioVigencia(d(2026, 12, 1), hoje, d(2026, 1, 1));
            assertThatThrownBy(() -> SlaCicloVida.validarInicioVigencia(d(2026, 11, 22), hoje, null))
                    .hasMessageContaining("retroativa");
            assertThatThrownBy(() -> SlaCicloVida.validarInicioVigencia(null, hoje, null)).hasMessageContaining("Informe");
            assertThatThrownBy(() -> SlaCicloVida.validarInicioVigencia(d(2026, 12, 1), hoje, d(2026, 12, 1)))
                    .hasMessageContaining("depois");
        }
    }

    @Nested
    class Notificacao {

        @Test
        void rn21PadraoDoEvento() {
            assertThat(RegraNotificacao.eventoValido("sustentacao.sla.violado")).isTrue();
            assertThat(RegraNotificacao.eventoValido("sustentacao.sla.em-risco")).isTrue();
            assertThat(RegraNotificacao.eventoValido("configuracao.equipe-contratada.alterada")).isTrue();
            assertThat(RegraNotificacao.eventoValido("Sustentacao.SLA.violado")).isFalse();
            assertThat(RegraNotificacao.eventoValido("sla.violado")).isFalse();
            assertThat(RegraNotificacao.eventoValido(null)).isFalse();
        }

        @Test
        void rn21EmailSemCpfNemConteudoDeChamado() {
            String modelo = "Olá {{usuario.nome}}, CPF {{ usuario.cpf }} - {{chamado.descricao}} {{chamado.codigo}}";
            assertThat(RegraNotificacao.variaveis(modelo)).containsExactly("usuario.nome", "usuario.cpf",
                    "chamado.descricao", "chamado.codigo");
            assertThat(RegraNotificacao.variaveisProibidasEmEmail(modelo)).containsExactly("usuario.cpf", "chamado.descricao");
            assertThat(RegraNotificacao.variaveisDesconhecidas(modelo, Set.of("usuario.nome", "chamado.codigo")))
                    .containsExactly("usuario.cpf", "chamado.descricao");
            assertThat(RegraNotificacao.variaveis(null)).isEmpty();
            assertThat(RegraNotificacao.renderizar("Chamado {{chamado.codigo}} {{x}}", Map.of("chamado.codigo", "SUP-1")))
                    .isEqualTo("Chamado SUP-1 ");
        }

        @Test
        void rn22LimiarSoNoAlertaDeRisco() {
            RegraNotificacao.validarLimiar("sustentacao.sla.em-risco", 80);
            RegraNotificacao.validarLimiar("sustentacao.sla.violado", null);
            assertThatThrownBy(() -> RegraNotificacao.validarLimiar("sustentacao.sla.em-risco", null))
                    .hasMessageContaining("limiar");
            assertThatThrownBy(() -> RegraNotificacao.validarLimiar("sustentacao.sla.em-risco", 100))
                    .hasMessageContaining("limiar");
            assertThatThrownBy(() -> RegraNotificacao.validarLimiar("sustentacao.sla.violado", 50))
                    .hasMessageContaining("só se aplica");
        }

        @Test
        void rn23CpfMascarado() {
            assertThat(Mascaras.cpf("123.456.789-09")).isEqualTo("***.456.789-**");
            assertThat(Mascaras.cpf("12345678909")).isEqualTo("***.456.789-**");
            assertThat(Mascaras.cpf("123")).isEqualTo("***.***.***-**");
            assertThat(Mascaras.cpf(null)).isNull();
        }
    }
}
