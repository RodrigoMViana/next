package br.com.subti.next.configuracao.domain.model;

import java.util.Map;
import java.util.UUID;

/** Entidade com histórico de alterações campo a campo (RF16). */
public interface Rastreavel {

    UUID getId();

    /** Nome da entidade no histórico (por exemplo, "contrato"). */
    String tipoEntidade();

    /** Fotografia dos campos rastreados, já formatados para exibição. */
    Map<String, String> snapshot();
}
