package br.com.subti.next.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.subti.next.configuracao.infrastructure.integracao.DadosFicticios;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Testes de integração da API interna com banco H2 (modo Oracle), migrações Flyway e a carga de
 * demonstração. Cobrem os requisitos funcionais RF01 a RF17 e os não funcionais RNF02, RNF04, RNF06,
 * RNF08 e RNF13 no nível do módulo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.MethodName.class)
class ApiIntegracaoTest {

    static final String ADMIN = TokenTeste.token(DadosFicticios.U_ADMIN, "Ana Administradora", List.of("ADMINISTRADOR"), null);
    static final String GESTOR = TokenTeste.token(DadosFicticios.U_GESTOR_TI, "Gustavo Gestor", List.of("GESTOR_TI"), null);
    static final String N1 = TokenTeste.token(DadosFicticios.U_N1, "Nina N1", List.of("N1"), null);
    static final String N2_ALFA = TokenTeste.token(DadosFicticios.U_N2_ALFA, "Fábio Gestor", List.of("N2_GESTOR"),
            DadosFicticios.FABRICA_ALFA);
    static final String N2_BETA = TokenTeste.token(DadosFicticios.U_N2_BETA, "Bruno Gestor", List.of("N2_GESTOR"),
            DadosFicticios.FABRICA_BETA);

    static final String HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

    @Autowired
    MockMvc mvc;

    static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder b, String token) {
        return b.header("X-Next-Internal-Token", token).header("X-Correlation-Id", "c-teste").contentType(MediaType.APPLICATION_JSON);
    }

    String id(MvcResult r) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    String primeiroId(String rota, String token) throws Exception {
        MvcResult r = mvc.perform(com(get(rota), token)).andExpect(status().isOk()).andReturn();

        return JsonPath.read(r.getResponse().getContentAsString(), "$.content[0].id");
    }

    @Test
    @DisplayName("RNF01/8.4: chamada sem token interno válido é recusada com Problem Details")
    void a01_semToken() throws Exception {
        mvc.perform(get("/internal/v1/contratos")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:next:erro:token-interno-invalido"));
        String outroSegredo = TokenTeste.token(DadosFicticios.U_ADMIN, "x", List.of("ADMINISTRADOR"), null,
                "um-segredo-diferente-com-mais-de-32-bytes!!", 60);
        mvc.perform(com(get("/internal/v1/contratos"), outroSegredo)).andExpect(status().isUnauthorized());
        String expirado = TokenTeste.token(DadosFicticios.U_ADMIN, "x", List.of("ADMINISTRADOR"), null, TokenTeste.SEGREDO, -5);
        mvc.perform(com(get("/internal/v1/contratos"), expirado)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Carga de demonstração disponível e painel com pendências")
    void a02_painel() throws Exception {
        mvc.perform(com(get("/internal/v1/painel"), GESTOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totais.contratosAtivos").value(2))
                .andExpect(jsonPath("$.pendencias[*].tipo", hasItem("SISTEMA_SEM_SLA")));
    }

    @Test
    @DisplayName("RF06/RNF13: CNPJ alfanumérico, máscara, unicidade, If-Match e ETag")
    void b01_contratada() throws Exception {
        mvc.perform(com(get("/internal/v1/contratadas/validacao-cnpj").param("valor", "12.abc.345/01de-35"), GESTOR))
                .andExpect(jsonPath("$.valido").value(true)).andExpect(jsonPath("$.alfanumerico").value(true))
                .andExpect(jsonPath("$.formatado").value("12.ABC.345/01DE-35"));

        mvc.perform(com(post("/internal/v1/contratadas"), GESTOR).content("""
                        {"cnpj":"12.ABC.345/01DE-36","razaoSocial":"Empresa X"}"""))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0].field").value("cnpj"))
                .andExpect(jsonPath("$.errors[0].message").value("Dígitos verificadores do CNPJ não conferem."))
                .andExpect(jsonPath("$.correlationId").value("c-teste"));

        MvcResult r = mvc.perform(com(post("/internal/v1/contratadas"), GESTOR).content("""
                        {"cnpj":"a1.b2c.3d4/e5f6-68","razaoSocial":"Gama Software Ltda."}"""))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.cnpj").value("A1B2C3D4E5F668"))
                .andExpect(jsonPath("$.cnpjFormatado").value("A1.B2C.3D4/E5F6-68")).andReturn();
        String id = id(r);

        mvc.perform(com(post("/internal/v1/contratadas"), GESTOR).content("""
                        {"cnpj":"A1B2C3D4E5F668","razaoSocial":"Duplicada"}"""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].field").value("cnpj"));

        String corpo = """
                {"cnpj":"A1B2C3D4E5F668","razaoSocial":"Gama Software S.A."}""";
        mvc.perform(com(put("/internal/v1/contratadas/" + id), GESTOR).content(corpo)).andExpect(status().is(428));
        mvc.perform(com(put("/internal/v1/contratadas/" + id), GESTOR).header("If-Match", "\"7\"").content(corpo))
                .andExpect(status().is(412));
        mvc.perform(com(put("/internal/v1/contratadas/" + id), GESTOR).header("If-Match", "\"0\"").content(corpo))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""));

        mvc.perform(com(get("/internal/v1/contratadas/" + id + "/historico"), GESTOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].campo").value("razaoSocial"))
                .andExpect(jsonPath("$[0].valorAnterior").value("Gama Software Ltda."))
                .andExpect(jsonPath("$[0].valorNovo").value("Gama Software S.A."))
                .andExpect(jsonPath("$[0].usuarioNome").value("Gustavo Gestor"));

        mvc.perform(com(post("/internal/v1/contratadas"), N1).content(corpo)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RF08: alteração contratual com anexo verificado por tipo e conteúdo")
    void b02_alteracaoContratual() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("anexo", "ata.pdf", "application/pdf", "%PDF-1.7 teste".getBytes());
        MockMultipartFile falso = new MockMultipartFile("anexo", "x.pdf", "application/pdf", "MZ executavel".getBytes());
        String base = "/internal/v1/contratadas/" + DadosFicticios.FABRICA_ALFA + "/alteracoes";
        mvc.perform(MockMvcRequestBuilders.multipart(base).file(pdf).param("dataAlteracao", "2026-01-10")
                        .param("tipo", "ALTERACAO_ENDERECO").param("descricao", "Mudança de sede")
                        .header("X-Next-Internal-Token", GESTOR))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.anexoNome").value("ata.pdf"));
        mvc.perform(MockMvcRequestBuilders.multipart(base).file(falso).param("dataAlteracao", "2026-01-10")
                        .param("tipo", "OUTRA").param("descricao", "x").header("X-Next-Internal-Token", GESTOR))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0].field").value("anexo"));
    }

    @Test
    @DisplayName("RF01 a RF05: contrato com idempotência, aditivos, fiscais, desativação e escopo do N2")
    void c01_contrato() throws Exception {
        String servico = primeiroId("/internal/v1/servicos?termo=DEV-PF", GESTOR);
        String calendario = primeiroId("/internal/v1/calendarios?termo=Expediente", GESTOR);
        String fonte = primeiroId("/internal/v1/fontes-recurso?termo=FR-1500", GESTOR);
        String semFiscal = """
                {"numero":"CT-2026-0100","objeto":"Teste","contratadaId":"%s","calendarioId":"%s",
                 "vigenciaInicio":"2026-01-01","vigenciaFim":"2026-12-31",
                 "itens":[{"servicoId":"%s","volume":100,"valorUnitario":10.5}],
                 "fonteIds":["%s"],"sistemaIds":["%s"],"fiscais":[]}
                """.formatted(DadosFicticios.FABRICA_BETA, calendario, servico, fonte, DadosFicticios.SIS_ESTOQUE);
        mvc.perform(com(post("/internal/v1/contratos"), GESTOR).content(semFiscal)).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0].field").value("fiscais"));

        String vigenciaInvertida = semFiscal.replace("\"vigenciaFim\":\"2026-12-31\"", "\"vigenciaFim\":\"2025-12-31\"");
        mvc.perform(com(post("/internal/v1/contratos"), GESTOR).content(vigenciaInvertida)).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0].field").value("vigenciaFim"));

        String valido = semFiscal.replace("\"fiscais\":[]", "\"fiscais\":[{\"usuarioId\":\"" + DadosFicticios.U_FISCAL_TEC
                + "\",\"tipoFiscal\":\"FISCAL_TECNICO\",\"inicio\":\"2026-01-01\"}]");
        MvcResult r = mvc.perform(com(post("/internal/v1/contratos"), GESTOR).header("Idempotency-Key", "k-ct-100").content(valido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorOriginal").value(1050.0))
                .andExpect(jsonPath("$.temFiscalVigente").value(true)).andReturn();
        String id = id(r);
        mvc.perform(com(post("/internal/v1/contratos"), GESTOR).header("Idempotency-Key", "k-ct-100").content(valido))
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(id));

        mvc.perform(com(post("/internal/v1/contratos/" + id + "/aditivos"), GESTOR).content("""
                        {"numero":"1º TA","aditivoPrazo":true,"aditivoValor":true,"novaVigenciaFim":"2027-06-30",
                         "valorAcrescimo":250.00,"dataAssinatura":"2026-06-01","justificativa":"Prorrogação e acréscimo"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigenciaFim").value("2026-12-31"))
                .andExpect(jsonPath("$.vigenciaFimEfetiva").value("2027-06-30"))
                .andExpect(jsonPath("$.valorOriginal").value(1050.0))
                .andExpect(jsonPath("$.valorEfetivo").value(1300.0));
        mvc.perform(com(post("/internal/v1/contratos/" + id + "/aditivos"), GESTOR).content("""
                        {"numero":"2º TA","aditivoPrazo":false,"aditivoValor":true,"valorAcrescimo":-5000,
                         "dataAssinatura":"2026-07-01","justificativa":"Supressão"}"""))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0].field").value("valorAcrescimo"));

        // RN06: não encerra o único fiscal; substitui
        MvcResult det = mvc.perform(com(get("/internal/v1/contratos/" + id), GESTOR)).andReturn();
        String fiscalId = JsonPath.read(det.getResponse().getContentAsString(), "$.fiscais[0].id");
        mvc.perform(com(post("/internal/v1/contratos/" + id + "/fiscais/" + fiscalId + "/encerramento"), GESTOR)
                .content("{\"fim\":\"" + HOJE + "\",\"motivo\":\"Aposentadoria\"}")).andExpect(status().is(422));
        mvc.perform(com(post("/internal/v1/contratos/" + id + "/fiscais/" + fiscalId + "/substituicao"), GESTOR)
                        .content("{\"usuarioId\":\"" + DadosFicticios.U_FISCAL_ADM + "\",\"inicio\":\"" + HOJE
                                + "\",\"motivo\":\"Remanejamento\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fiscais.length()").value(2))
                .andExpect(jsonPath("$.temFiscalVigente").value(true));

        // RF05: desativação exige justificativa
        mvc.perform(com(post("/internal/v1/contratos/" + id + "/desativacao"), GESTOR).content("{}")).andExpect(status().is(422));
        mvc.perform(com(post("/internal/v1/contratos/" + id + "/desativacao"), GESTOR).content("{\"justificativa\":\"Rescisão\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("DESATIVADO"));

        // RF16: histórico com aditivo, substituição e desativação
        mvc.perform(com(get("/internal/v1/contratos/" + id + "/historico"), GESTOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].acao", hasItem("ADITIVO")))
                .andExpect(jsonPath("$[*].acao", hasItem("FISCAL_SUBSTITUICAO")))
                .andExpect(jsonPath("$[*].acao", hasItem("DESATIVACAO")));

        // RNF02: N2 gestor da Alfa não enxerga contrato da Beta; lista só a própria fábrica
        mvc.perform(com(get("/internal/v1/contratos/" + id), N2_ALFA)).andExpect(status().isNotFound());
        mvc.perform(com(get("/internal/v1/contratos"), N2_BETA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].contratada.id", everyItem(is(DadosFicticios.FABRICA_BETA.toString()))));

        // RF01: filtros e paginação
        mvc.perform(com(get("/internal/v1/contratos").param("situacao", "ATIVO").param("size", "1").param("sort", "numero,desc"), N1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("RF09/RF17/RN14: SLA versionado com parecer da fábrica e decisão de vigência")
    void d01_sla() throws Exception {
        MvcResult lista = mvc.perform(com(get("/internal/v1/slas").param("termo", "SLA-ALFA"), GESTOR)).andReturn();
        String slaId = JsonPath.read(lista.getResponse().getContentAsString(), "$.content[0].id");
        mvc.perform(com(get("/internal/v1/slas/" + slaId + "/versao-vigente"), GESTOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.versao.numero").value(1));

        MvcResult nova = mvc.perform(com(post("/internal/v1/slas/" + slaId + "/versoes"), GESTOR)
                .content("{\"motivoAlteracao\":\"Reduzir prazo de solução\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(2)).andExpect(jsonPath("$.situacao").value("RASCUNHO"))
                .andExpect(jsonPath("$.regras.length()").value(4)).andReturn();
        String v2 = id(nova);
        mvc.perform(com(post("/internal/v1/slas/" + slaId + "/versoes"), GESTOR).content("{\"motivoAlteracao\":\"x\"}"))
                .andExpect(status().isConflict());

        mvc.perform(com(put("/internal/v1/slas/" + slaId + "/versoes/" + v2 + "/regras"), GESTOR).header("If-Match", "\"0\"")
                        .content("""
                                {"regras":[{"classe":"Crítica","indicador":"Tempo de solução","metaValor":2,
                                 "metaUnidade":"HORAS","formaContagem":"HORARIO_COMERCIAL","permitePausa":true}]}"""))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0].message", containsString("calendário")));

        mvc.perform(com(post("/internal/v1/slas/" + slaId + "/versoes/" + v2 + "/envio-avaliacao"), GESTOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("EM_AVALIACAO"));
        mvc.perform(com(post("/internal/v1/slas/" + slaId + "/versoes/" + v2 + "/decisao"), GESTOR)
                        .content("{\"aprovar\":true,\"vigenciaInicio\":\"" + HOJE + "\"}"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0].field").value("parecer"));
        mvc.perform(com(post("/internal/v1/slas/" + slaId + "/versoes/" + v2 + "/parecer"), N2_BETA)
                .content("{\"favoravel\":true,\"texto\":\"ok\"}")).andExpect(status().isForbidden());
        mvc.perform(com(post("/internal/v1/slas/" + slaId + "/versoes/" + v2 + "/parecer"), N2_ALFA)
                        .content("{\"favoravel\":true,\"texto\":\"De acordo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parecerPorNome").value("Fábio Gestor"));
        mvc.perform(com(post("/internal/v1/slas/" + slaId + "/versoes/" + v2 + "/decisao"), GESTOR)
                        .content("{\"aprovar\":true,\"vigenciaInicio\":\"2020-01-01\"}"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0].message", containsString("retroativa")));
        String amanha = LocalDate.parse(HOJE).plusDays(1).toString();
        mvc.perform(com(post("/internal/v1/slas/" + slaId + "/versoes/" + v2 + "/decisao"), GESTOR)
                        .content("{\"aprovar\":true,\"vigenciaInicio\":\"" + amanha + "\",\"observacao\":\"Aprovado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("VIGENTE"));
        // chamados de hoje continuam na versão 1; a partir de amanhã, versão 2 (sem efeito retroativo)
        mvc.perform(com(get("/internal/v1/slas/" + slaId + "/versao-vigente").param("data", HOJE), GESTOR))
                .andExpect(jsonPath("$.versao.numero").value(1));
        mvc.perform(com(get("/internal/v1/slas/" + slaId + "/versao-vigente").param("data", amanha), GESTOR))
                .andExpect(jsonPath("$.versao.numero").value(2));

        // simulação de prazo de uma regra
        MvcResult det = mvc.perform(com(get("/internal/v1/slas/" + slaId), GESTOR)).andReturn();
        String regraId = JsonPath.read(det.getResponse().getContentAsString(), "$.versoes[1].regras[1].id");
        mvc.perform(com(post("/internal/v1/slas/" + slaId + "/simulacoes"), N1).content("""
                        {"regraId":"%s","inicio":"2026-11-23T11:00:00Z","agora":"2026-11-23T13:00:00Z","limiarPercentual":40}
                        """.formatted(regraId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.minutosMeta").value(240))
                .andExpect(jsonPath("$.situacao").value("EM_RISCO"));
    }

    @Test
    @DisplayName("RF10/RN20: portfólio sinaliza sistemas ativos sem SLA vigente e evento idempotente")
    void e01_portfolio() throws Exception {
        mvc.perform(com(get("/internal/v1/portfolio/sistemas").param("somentePendentes", "true"), N1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sistemas[*].sigla", hasItem("PROT")));
        mvc.perform(com(get("/internal/v1/portfolio/sistemas"), N2_ALFA)).andExpect(status().isForbidden());
        String evento = """
                {"id":"%s","type":"portfolio.sistema.ativado","version":1,"payload":{"sistemaId":"%s","nome":"Arrecadação Web"}}
                """.formatted(UUID.randomUUID(), DadosFicticios.SIS_ARRECADACAO);
        mvc.perform(com(post("/internal/v1/eventos"), ADMIN).content(evento)).andExpect(jsonPath("$.processado").value(true));
        mvc.perform(com(post("/internal/v1/eventos"), ADMIN).content(evento)).andExpect(jsonPath("$.processado").value(false));
    }

    @Test
    @DisplayName("RF13/RN21/RN22: notificação valida evento, limiar e e-mail sem CPF")
    void f01_notificacao() throws Exception {
        mvc.perform(com(post("/internal/v1/notificacoes"), GESTOR).content("""
                        {"nome":"x","evento":"sustentacao.sla.violado","canalApp":false,"canalEmail":true,"perfis":["N1"],
                         "assunto":"SLA","modelo":"CPF {{usuario.cpf}}"}"""))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0].field").value("modelo"));
        mvc.perform(com(post("/internal/v1/notificacoes"), GESTOR).content("""
                        {"nome":"x","evento":"sustentacao.sla.em-risco","canalApp":true,"canalEmail":false,"perfis":["N1"],
                         "assunto":"SLA","modelo":"{{chamado.codigo}}"}"""))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0].field").value("limiarPercentual"));
        mvc.perform(com(post("/internal/v1/notificacoes"), GESTOR).content("""
                        {"nome":"Aviso de violação","evento":"sustentacao.sla.violado","canalApp":true,"canalEmail":true,
                         "perfis":["N1","GESTOR_TI"],"assunto":"SLA {{sla.codigo}}","modelo":"Chamado {{chamado.codigo}} venceu"}"""))
                .andExpect(status().isCreated());
        mvc.perform(com(get("/internal/v1/notificacoes"), N1)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RF11/RNF04: consulta de governança com filtros, somente gestão")
    void g01_governanca() throws Exception {
        MvcResult r = mvc.perform(com(get("/internal/v1/auditoria/governanca").param("acao", "SLA_PARECER"), ADMIN))
                .andExpect(status().isOk()).andReturn();
        int total = JsonPath.read(r.getResponse().getContentAsString(), "$.totalElements");
        assertThat(total).isGreaterThanOrEqualTo(1);
        mvc.perform(com(get("/internal/v1/auditoria/governanca"), N1)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN15/RN18: desativação de item em uso exige justificativa; calendário não retroage")
    void h01_apoioECalendario() throws Exception {
        String unidade = primeiroId("/internal/v1/unidades-medida?termo=PF", GESTOR);
        mvc.perform(com(post("/internal/v1/unidades-medida/" + unidade + "/desativacao"), GESTOR).content("{}"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0].field").value("justificativa"));
        String calendario = primeiroId("/internal/v1/calendarios?termo=Plant", GESTOR);
        MvcResult cal = mvc.perform(com(get("/internal/v1/calendarios/" + calendario), GESTOR)).andReturn();
        String etag = cal.getResponse().getHeader("ETag");
        mvc.perform(com(put("/internal/v1/calendarios/" + calendario), GESTOR).header("If-Match", etag).content("""
                        {"nome":"Plantão estendido","fusoHorario":"America/Sao_Paulo","vigenciaInicio":"2020-01-01",
                         "expedientes":[{"diaSemana":1,"horaInicio":"08:00","horaFim":"12:00"}],"feriados":[]}"""))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0].field").value("vigenciaInicio"));
        mvc.perform(com(post("/internal/v1/calendarios/" + calendario + "/simulacoes"), GESTOR).content("""
                        {"inicio":"2026-11-23T10:00:00Z","meta":2,"unidade":"HORAS","formaContagem":"HORARIO_COMERCIAL"}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.minutosUteis").value(120));
    }
}
