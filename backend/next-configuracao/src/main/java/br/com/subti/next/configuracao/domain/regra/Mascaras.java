package br.com.subti.next.configuracao.domain.regra;

/** Mascaramento de dados pessoais (RN23, RNF05). */
public final class Mascaras {

    private Mascaras() {
    }

    /** CPF no formato ***.456.789-** (exibe só os dígitos centrais). */
    public static String cpf(String cpf) {
        if (cpf == null) {
            return null;
        }
        String d = cpf.replaceAll("\\D", "");
        if (d.length() != 11) {
            return "***.***.***-**";
        }
        return "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**";
    }
}
