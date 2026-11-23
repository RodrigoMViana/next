package br.com.subti.next.configuracao.domain.regra;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras de configuração de notificações (RN21 e RN22).
 */
public final class RegraNotificacao {

    /** Nome do evento: modulo.entidade.fato-no-passado, em minúsculas e kebab-case. */
    public static final Pattern PADRAO_EVENTO =
            Pattern.compile("^[a-z]+\\.[a-z]+(-[a-z]+)*\\.[a-z]+(-[a-z]+)*$");

    /** Variáveis do modelo: {{nome}} ou {{objeto.campo}}. */
    private static final Pattern VARIAVEL = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_.]*)\\s*}}");

    /** RN21: e-mail não exibe CPF nem conteúdo de chamados. */
    private static final Set<String> PROIBIDAS_EMAIL = Set.of("cpf", "descricao", "conteudo", "comentario",
            "mensagem", "anexo", "telefone", "email");

    public static final String EVENTO_SLA_EM_RISCO = "sustentacao.sla.em-risco";

    private RegraNotificacao() {
    }

    public static boolean eventoValido(String evento) {
        return evento != null && PADRAO_EVENTO.matcher(evento).matches();
    }

    public static List<String> variaveis(String modelo) {
        List<String> nomes = new ArrayList<>();
        if (modelo == null) {
            return nomes;
        }
        Matcher m = VARIAVEL.matcher(modelo);
        while (m.find()) {
            nomes.add(m.group(1));
        }
        return nomes;
    }

    /** Variáveis do modelo que não podem ir por e-mail (RN21). */
    public static List<String> variaveisProibidasEmEmail(String modelo) {
        return variaveis(modelo).stream().filter(v -> {
            String ultimo = v.contains(".") ? v.substring(v.lastIndexOf('.') + 1) : v;
            return PROIBIDAS_EMAIL.contains(ultimo.toLowerCase(Locale.ROOT));
        }).distinct().toList();
    }

    /** Variáveis do modelo que não estão na lista do evento. */
    public static List<String> variaveisDesconhecidas(String modelo, Set<String> permitidas) {
        return variaveis(modelo).stream().filter(v -> !permitidas.contains(v)).distinct().toList();
    }

    /** RN22: limiar obrigatório (1 a 99%) no alerta de SLA em risco; proibido nos demais eventos. */
    public static void validarLimiar(String evento, Integer limiar) {
        if (EVENTO_SLA_EM_RISCO.equals(evento)) {
            if (limiar == null || limiar < 1 || limiar > 99) {
                throw new IllegalArgumentException("Informe o limiar de alerta entre 1% e 99% do prazo consumido.");
            }
        } else if (limiar != null) {
            throw new IllegalArgumentException("O limiar de alerta só se aplica ao evento " + EVENTO_SLA_EM_RISCO + ".");
        }
    }

    /** Substitui as variáveis do modelo pelos valores informados (pré-visualização). */
    public static String renderizar(String modelo, Map<String, String> valores) {
        Matcher m = VARIAVEL.matcher(modelo);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(valores.getOrDefault(m.group(1), "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
