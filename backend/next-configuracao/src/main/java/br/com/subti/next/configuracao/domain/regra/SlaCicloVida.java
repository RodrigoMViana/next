package br.com.subti.next.configuracao.domain.regra;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida da versão do SLA (RN14 e RF17): Rascunho → Em avaliação → Vigente → Encerrada.
 * Uma versão em avaliação pode ser devolvida para Rascunho.
 */
public final class SlaCicloVida {

    public enum SituacaoVersao { RASCUNHO, EM_AVALIACAO, VIGENTE, ENCERRADA }

    private static final Map<SituacaoVersao, Set<SituacaoVersao>> TRANSICOES = Map.of(
            SituacaoVersao.RASCUNHO, Set.of(SituacaoVersao.EM_AVALIACAO),
            SituacaoVersao.EM_AVALIACAO, Set.of(SituacaoVersao.RASCUNHO, SituacaoVersao.VIGENTE),
            SituacaoVersao.VIGENTE, Set.of(SituacaoVersao.ENCERRADA),
            SituacaoVersao.ENCERRADA, Set.of());

    private SlaCicloVida() {
    }

    public static boolean podeTransitar(SituacaoVersao de, SituacaoVersao para) {
        return TRANSICOES.get(de).contains(para);
    }

    public static void exigirTransicao(SituacaoVersao de, SituacaoVersao para) {
        if (!podeTransitar(de, para)) {
            throw new IllegalStateException("A versão está " + rotulo(de) + " e não pode passar para " + rotulo(para) + ".");
        }
    }

    /** Somente versões em rascunho têm regras editáveis. */
    public static boolean editavel(SituacaoVersao situacao) {
        return situacao == SituacaoVersao.RASCUNHO;
    }

    /**
     * RN14: a vigência de uma nova versão não retroage. Ela começa hoje ou depois, e depois do início
     * da versão vigente anterior.
     */
    public static void validarInicioVigencia(LocalDate inicio, LocalDate hoje, LocalDate inicioVigenteAnterior) {
        if (inicio == null) {
            throw new IllegalArgumentException("Informe o início da vigência da versão.");
        }
        if (inicio.isBefore(hoje)) {
            throw new IllegalArgumentException("A vigência não pode ser retroativa: informe hoje ou uma data futura.");
        }
        if (inicioVigenteAnterior != null && !inicio.isAfter(inicioVigenteAnterior)) {
            throw new IllegalArgumentException("A nova vigência deve começar depois do início da versão vigente.");
        }
    }

    public static String rotulo(SituacaoVersao s) {
        return switch (s) {
            case RASCUNHO -> "em rascunho";
            case EM_AVALIACAO -> "em avaliação";
            case VIGENTE -> "vigente";
            case ENCERRADA -> "encerrada";
        };
    }
}
