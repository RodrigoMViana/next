package br.com.subti.next.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.subti.next.bff.seguranca.Rbac;
import br.com.subti.next.bff.seguranca.UsuarioSessao;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Testes do BFF sem o módulo no ar (o módulo aponta para uma porta fechada). */
@SpringBootTest(properties = "next.modulos.configuracao.url=http://127.0.0.1:9")
@AutoConfigureMockMvc
class BffIntegracaoTest {

    static final String GESTOR = "10000000-0000-4000-8000-000000000002";
    static final String N1 = "10000000-0000-4000-8000-000000000003";
    static final String N2_ALFA = "10000000-0000-4000-8000-000000000004";

    @Autowired
    MockMvc mvc;

    MockHttpSession login(String usuario, String mfa) throws Exception {
        MockHttpSession s = new MockHttpSession();
        String corpo = mfa == null ? "{\"usuarioId\":\"" + usuario + "\"}"
                : "{\"usuarioId\":\"" + usuario + "\",\"codigoMfa\":\"" + mfa + "\"}";
        MvcResult r = mvc.perform(post("/api/v1/auth/login").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo)).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    @Test
    @DisplayName("Sem sessão: 401 Problem Details com correlationId e cabeçalhos de segurança")
    void semSessao() throws Exception {
        mvc.perform(get("/api/v1/configuracao/contratos")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:next:erro:nao-autenticado"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
    }

    @Test
    @DisplayName("RNF01: escrita sem token anti-CSRF é recusada")
    void csrfObrigatorio() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":\"" + GESTOR + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    @DisplayName("RNF03/RN09: MFA obrigatório para usuário de fábrica; CPF mascarado na sessão")
    void mfa() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + N2_ALFA + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.type").value("urn:next:erro:mfa-obrigatorio"))
                .andExpect(jsonPath("$.errors[0].field").value("codigoMfa"));
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + N2_ALFA + "\",\"codigoMfa\":\"000000\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.type").value("urn:next:erro:mfa-invalido"));
        MockHttpSession s = login(N2_ALFA, "123456");
        mvc.perform(get("/api/v1/auth/sessao").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cpfMascarado").value("***.287.938-**"))
                .andExpect(jsonPath("$.permissoes.parecerSla").value(true))
                .andExpect(jsonPath("$.permissoes.escreverCadastros").value(false));
    }

    @Test
    @DisplayName("RNF02: RBAC no BFF antes de chamar o módulo; módulo fora do ar vira 503")
    void rbacEDegradacao() throws Exception {
        MockHttpSession n1 = login(N1, null);
        mvc.perform(post("/api/v1/configuracao/contratos").session(n1).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("urn:next:erro:acesso-negado"));
        mvc.perform(get("/api/v1/configuracao/notificacoes").session(n1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auditoria/acessos").session(n1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/configuracao/contratos").session(n1)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:next:erro:modulo-indisponivel"));
    }

    @Test
    @DisplayName("N05/RF11/RF12: trilha de acesso consultável e exportação assíncrona com link temporário")
    void trilhaEExportacao() throws Exception {
        MockHttpSession g = login(GESTOR, null);
        mvc.perform(get("/api/v1/configuracao/contratos").session(g));
        mvc.perform(get("/api/v1/auditoria/acessos").session(g).param("usuarioId", GESTOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].rota", hasItem("/api/v1/configuracao/contratos")))
                .andExpect(jsonPath("$.content[*].acao", hasItem("LOGIN")));

        MvcResult r = mvc.perform(post("/api/v1/auditoria/exportacoes").session(g).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"ACESSOS\",\"filtros\":{\"usuarioId\":\"" + GESTOR + "\"}}"))
                .andExpect(status().isAccepted()).andReturn();
        String id = JsonPath.read(r.getResponse().getContentAsString(), "$.id");
        String situacao = "PROCESSANDO";
        for (int i = 0; i < 50 && situacao.equals("PROCESSANDO"); i++) {
            Thread.sleep(100);
            MvcResult l = mvc.perform(get("/api/v1/auditoria/exportacoes").session(g)).andReturn();
            situacao = JsonPath.read(l.getResponse().getContentAsString(), "$[0].situacao");
        }
        assertThat(situacao).isEqualTo("CONCLUIDA");
        MvcResult link = mvc.perform(post("/api/v1/auditoria/exportacoes/" + id + "/link").session(g).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        String url = JsonPath.read(link.getResponse().getContentAsString(), "$.url");
        MvcResult arq = mvc.perform(get(url).session(g)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(arq.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("ocorrido_em;usuario_id");
        mvc.perform(get("/api/v1/auditoria/exportacoes/" + id + "/arquivo").session(g).param("token", "invalido"))
                .andExpect(status().isNotFound());
        // outro usuário de gestão não baixa o arquivo de quem solicitou
        MockHttpSession admin = login("10000000-0000-4000-8000-000000000001", null);
        mvc.perform(get(url).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Regras de RBAC da seção 5.4 do DOD")
    void regrasRbac() {
        UsuarioSessao n1 = new UsuarioSessao("1", "n1", null, Set.of("N1"), null, false, Instant.now());
        UsuarioSessao n2 = new UsuarioSessao("2", "n2", null, Set.of("N2_GESTOR"), "f", true, Instant.now());
        UsuarioSessao gestor = new UsuarioSessao("3", "g", null, Set.of("GESTOR_TI"), null, false, Instant.now());
        UsuarioSessao tecnico = new UsuarioSessao("4", "t", null, Set.of("N2_TECNICO"), "f", true, Instant.now());
        String id = "00000000-0000-4000-8000-000000000001";
        assertThat(Rbac.permitido(n1, "GET", "/contratos")).isTrue();
        assertThat(Rbac.permitido(n1, "POST", "/contratos")).isFalse();
        assertThat(Rbac.permitido(n2, "GET", "/portfolio/sistemas")).isFalse();
        assertThat(Rbac.permitido(n1, "GET", "/portfolio/sistemas")).isTrue();
        assertThat(Rbac.permitido(n2, "POST", "/slas/" + id + "/versoes/" + id + "/parecer")).isTrue();
        assertThat(Rbac.permitido(gestor, "POST", "/slas/" + id + "/versoes/" + id + "/parecer")).isFalse();
        assertThat(Rbac.permitido(n2, "POST", "/slas/" + id + "/simulacoes")).isTrue();
        assertThat(Rbac.permitido(gestor, "PUT", "/contratos/" + id)).isTrue();
        assertThat(Rbac.permitido(n1, "GET", "/auditoria/governanca")).isFalse();
        assertThat(Rbac.permitido(gestor, "GET", "/rota-desconhecida")).isFalse();
        assertThat(Rbac.acessaModulo(tecnico)).isFalse();
        assertThat(Rbac.permitido(tecnico, "GET", "/contratos")).isFalse();
    }
}
