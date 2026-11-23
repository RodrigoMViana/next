package br.com.subti.next.configuracao.domain.regra;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Regras da comissão de fiscalização (RN06).
 */
public final class Fiscalizacao {

    /** Designação de um fiscal: período de início e fim (fim nulo = sem data de término). */
    public record Designacao(LocalDate inicio, LocalDate fim) {
        public boolean vigenteEm(LocalDate data) {
            return !data.isBefore(inicio) && (fim == null || !data.isAfter(fim));
        }
    }

    private Fiscalizacao() {
    }

    /** RN06: o contrato deve ter ao menos um fiscal com designação vigente. */
    public static boolean temFiscalVigente(Collection<Designacao> designacoes, LocalDate data) {
        return designacoes.stream().anyMatch(d -> d.vigenteEm(data));
    }

    public static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null) {
            throw new IllegalArgumentException("Informe o início da designação.");
        }
        if (fim != null && fim.isBefore(inicio)) {
            throw new IllegalArgumentException("O fim da designação não pode ser anterior ao início.");
        }
    }

    /**
     * Substituição: a designação anterior termina no dia anterior ao início da nova.
     * A data de início da nova designação não pode ser anterior ao início da anterior.
     */
    public static LocalDate fimDaAnterior(LocalDate inicioAnterior, LocalDate inicioNova) {
        if (inicioNova == null) {
            throw new IllegalArgumentException("Informe o início da nova designação.");
        }
        if (!inicioNova.isAfter(inicioAnterior)) {
            throw new IllegalArgumentException("A nova designação deve começar depois do início da designação substituída.");
        }
        return inicioNova.minusDays(1);
    }
}
