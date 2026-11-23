package br.com.subti.next.configuracao.infrastructure.integracao;

import java.util.UUID;

/**
 * Identificadores fixos dos dados fictícios usados em desenvolvimento, homologação e demonstração
 * (Premissa 8 do DOD: nenhum dado real). Os mesmos ids são usados pelo BFF no login de desenvolvimento.
 */
public final class DadosFicticios {

    public static final UUID FABRICA_ALFA = UUID.fromString("a1fa0000-0000-4000-8000-000000000001");
    public static final UUID FABRICA_BETA = UUID.fromString("be7a0000-0000-4000-8000-000000000002");

    public static final String U_ADMIN = "10000000-0000-4000-8000-000000000001";
    public static final String U_GESTOR_TI = "10000000-0000-4000-8000-000000000002";
    public static final String U_N1 = "10000000-0000-4000-8000-000000000003";
    public static final String U_N2_ALFA = "10000000-0000-4000-8000-000000000004";
    public static final String U_TEC_ALFA = "10000000-0000-4000-8000-000000000005";
    public static final String U_N2_BETA = "10000000-0000-4000-8000-000000000006";
    public static final String U_FISCAL_TEC = "10000000-0000-4000-8000-000000000007";
    public static final String U_FISCAL_ADM = "10000000-0000-4000-8000-000000000008";
    public static final String U_CLIENTE = "10000000-0000-4000-8000-000000000009";
    public static final String U_FISCAL_REQ = "10000000-0000-4000-8000-000000000010";

    public static final UUID SIS_ARRECADACAO = UUID.fromString("5a000000-0000-4000-8000-000000000001");
    public static final UUID SIS_PROTOCOLO = UUID.fromString("5a000000-0000-4000-8000-000000000002");
    public static final UUID SIS_PORTAL = UUID.fromString("5a000000-0000-4000-8000-000000000003");
    public static final UUID SIS_FOLHA = UUID.fromString("5a000000-0000-4000-8000-000000000004");
    public static final UUID SIS_ESTOQUE = UUID.fromString("5a000000-0000-4000-8000-000000000005");
    public static final UUID SIS_LEGADO = UUID.fromString("5a000000-0000-4000-8000-000000000006");

    private DadosFicticios() {
    }
}
