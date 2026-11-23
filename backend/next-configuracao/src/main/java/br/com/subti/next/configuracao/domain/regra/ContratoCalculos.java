package br.com.subti.next.configuracao.domain.regra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/**
 * Regras de cálculo do contrato (RN01, RN02 e RN05).
 *
 * <p>Os valores originais nunca são sobrescritos: vigência e valor efetivos são derivados
 * dos aditivos, que só admitem inclusão.</p>
 */
public final class ContratoCalculos {

    /** Item contratado: volume × valor unitário. */
    public record Item(BigDecimal volume, BigDecimal valorUnitario) {
    }

    /** Aditivo de prazo (nova data de fim) e/ou de valor (acréscimo, negativo para supressão). */
    public record Aditivo(LocalDate dataAssinatura, LocalDate novaVigenciaFim, BigDecimal valorAcrescimo) {
    }

    private ContratoCalculos() {
    }

    /** RN01: o fim da vigência não pode ser anterior ao início. */
    public static void validarVigencia(LocalDate inicio, LocalDate fim) {
        if (inicio == null) {
            throw new IllegalArgumentException("Informe o início da vigência.");
        }
        if (fim == null) {
            throw new IllegalArgumentException("Informe o fim da vigência.");
        }
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data de fim da vigência não pode ser anterior à de início.");
        }
    }

    /** RN02: valor total do item = volume × valor unitário, arredondado em centavos. */
    public static BigDecimal valorItem(BigDecimal volume, BigDecimal valorUnitario) {
        if (volume == null || volume.signum() <= 0) {
            throw new IllegalArgumentException("O volume contratado deve ser maior que zero.");
        }
        if (valorUnitario == null || valorUnitario.signum() < 0) {
            throw new IllegalArgumentException("O valor unitário não pode ser negativo.");
        }
        return volume.multiply(valorUnitario).setScale(2, RoundingMode.HALF_EVEN);
    }

    /** Soma dos itens (valor original do contrato). */
    public static BigDecimal valorOriginal(Collection<Item> itens) {
        return itens.stream().map(i -> valorItem(i.volume(), i.valorUnitario()))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** RN02/RN05: valor efetivo = soma dos itens + aditivos de valor. */
    public static BigDecimal valorEfetivo(Collection<Item> itens, Collection<Aditivo> aditivos) {
        BigDecimal acrescimos = aditivos.stream().map(Aditivo::valorAcrescimo).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal efetivo = valorOriginal(itens).add(acrescimos).setScale(2, RoundingMode.HALF_EVEN);
        if (efetivo.signum() < 0) {
            throw new IllegalArgumentException("Os aditivos de supressão não podem tornar o valor do contrato negativo.");
        }
        return efetivo;
    }

    /** RN05: vigência efetiva = nova data de fim do aditivo de prazo mais recente (ou a original). */
    public static LocalDate vigenciaFimEfetiva(LocalDate fimOriginal, Collection<Aditivo> aditivos) {
        return aditivos.stream().filter(a -> a.novaVigenciaFim() != null)
                .max(Comparator.comparing(Aditivo::dataAssinatura).thenComparing(Aditivo::novaVigenciaFim))
                .map(Aditivo::novaVigenciaFim).orElse(fimOriginal);
    }

    /** Valida um novo aditivo de prazo em relação à vigência efetiva atual. */
    public static void validarAditivoPrazo(LocalDate inicio, LocalDate fimEfetivoAtual, LocalDate novoFim) {
        if (novoFim == null) {
            throw new IllegalArgumentException("Informe a nova data de fim da vigência.");
        }
        if (novoFim.isBefore(inicio)) {
            throw new IllegalArgumentException("A nova data de fim não pode ser anterior ao início da vigência.");
        }
        if (novoFim.equals(fimEfetivoAtual)) {
            throw new IllegalArgumentException("A nova data de fim é igual à vigência efetiva atual.");
        }
    }

    /** RN01: o contrato só pode ser usado dentro da vigência efetiva. */
    public static boolean vigenteEm(LocalDate data, LocalDate inicio, LocalDate fimEfetivo) {
        return !data.isBefore(inicio) && !data.isAfter(fimEfetivo);
    }
}
