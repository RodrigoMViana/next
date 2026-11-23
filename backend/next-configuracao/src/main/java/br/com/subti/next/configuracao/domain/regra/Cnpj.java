package br.com.subti.next.configuracao.domain.regra;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Objeto de valor CNPJ, numérico ou alfanumérico (IN RFB nº 2.229/2024).
 *
 * <ul>
 *   <li>RN25: 14 posições — raiz (8), ordem (4) e DV (2). As 12 primeiras aceitam A–Z e 0–9;
 *   as 2 últimas são numéricas. Minúsculas são convertidas; acentos e símbolos rejeitados.</li>
 *   <li>RN26: o CNPJ numérico continua válido e não é convertido.</li>
 *   <li>RN27: DV por módulo 11, valor do caractere = ASCII − 48, pesos 2..9 da direita para a esquerda.</li>
 *   <li>RN28: armazenado sem máscara, em maiúsculas; exibido como XX.XXX.XXX/XXXX-XX.</li>
 * </ul>
 */
public final class Cnpj {

    /** Padrão declarado no contrato OpenAPI (RNF13). */
    public static final String PADRAO = "^[A-Z0-9]{12}[0-9]{2}$";

    private static final Pattern FORMATO = Pattern.compile(PADRAO);
    private static final Pattern MASCARA = Pattern.compile("[./\\-\\s]");

    private final String valor;

    private Cnpj(String valor) {
        this.valor = valor;
    }

    /**
     * Valida e cria o CNPJ a partir de um texto com ou sem máscara.
     *
     * @throws IllegalArgumentException com a mensagem a ser exibida no campo
     */
    public static Cnpj of(String entrada) {
        Optional<String> erro = validar(entrada);
        if (erro.isPresent()) {
            throw new IllegalArgumentException(erro.get());
        }
        return new Cnpj(normalizar(entrada));
    }

    /** Remove a máscara e converte para maiúsculas (RN28). Não valida. */
    public static String normalizar(String entrada) {
        if (entrada == null) {
            return "";
        }
        return MASCARA.matcher(entrada.trim()).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /** Retorna a mensagem de erro do campo, ou vazio quando o CNPJ é válido. */
    public static Optional<String> validar(String entrada) {
        String v = normalizar(entrada);
        if (v.isEmpty()) {
            return Optional.of("Informe o CNPJ.");
        }
        if (v.length() != 14) {
            return Optional.of("O CNPJ deve ter 14 posições (sem contar a máscara).");
        }
        if (!FORMATO.matcher(v).matches()) {
            if (!v.substring(12).chars().allMatch(Character::isDigit)) {
                return Optional.of("Os dois dígitos verificadores do CNPJ devem ser numéricos.");
            }
            return Optional.of("O CNPJ aceita apenas letras de A a Z e números de 0 a 9, sem acentos ou símbolos.");
        }
        if (v.chars().allMatch(c -> c == v.charAt(0))) {
            return Optional.of("CNPJ inválido: todos os caracteres são iguais.");
        }
        String dv = calcularDigitos(v.substring(0, 12));
        if (!dv.equals(v.substring(12))) {
            return Optional.of("Dígitos verificadores do CNPJ não conferem.");
        }
        return Optional.empty();
    }

    public static boolean valido(String entrada) {
        return validar(entrada).isEmpty();
    }

    /** Calcula os dois dígitos verificadores para as 12 primeiras posições (RN27). */
    public static String calcularDigitos(String base12) {
        if (base12 == null || base12.length() != 12) {
            throw new IllegalArgumentException("A base do CNPJ deve ter 12 posições.");
        }
        int dv1 = digito(base12);
        int dv2 = digito(base12 + dv1);
        return "" + dv1 + dv2;
    }

    private static int digito(String texto) {
        int soma = 0;
        int peso = 2;
        for (int i = texto.length() - 1; i >= 0; i--) {
            int valorCaractere = texto.charAt(i) - 48;
            soma += valorCaractere * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** Aplica a máscara XX.XXX.XXX/XXXX-XX a um valor normalizado. */
    public static String formatar(String v) {
        if (v == null || v.length() != 14) {
            return v;
        }
        return v.substring(0, 2) + "." + v.substring(2, 5) + "." + v.substring(5, 8) + "/"
                + v.substring(8, 12) + "-" + v.substring(12);
    }

    public boolean alfanumerico() {
        return !valor.chars().allMatch(Character::isDigit);
    }

    public String valor() {
        return valor;
    }

    public String formatado() {
        return formatar(valor);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Cnpj c && c.valor.equals(valor);
    }

    @Override
    public int hashCode() {
        return valor.hashCode();
    }

    @Override
    public String toString() {
        return formatado();
    }
}
