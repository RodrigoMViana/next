# 1. Documento de Arquitetura — Módulo Configuração (Next)

> Ferramenta de Gestão de Demandas de Desenvolvimento e Manutenção de Software — Módulo Configuração (N01 a N10).
> Este documento descreve **o que foi construído** e como se encaixa no *Documento de Arquitetura de Software do Next* (v0.1, decisões D1–D51) e no *Complemento DOD v02*.

| Item | Valor |
|---|---|
| Versão do produto | 1.0.0 |
| Data | 23/11/2026 |
| Tecnologias | Java 25 (LTS) · Spring Boot 4.2 · Vue 3.5 + TypeScript · Oracle (19c+; validado em 23ai Free) · Flyway · Vite 8 |
| Repositório | monorepo `next` (backend, frontend, infra, docs) |

---

## 1.1 Visão geral

O módulo concentra os **cadastros de apoio e parametrizações** usados pelos demais módulos do Next: a Sustentação usa contratos, SLAs e calendários para medir prazos; a Engenharia usa contratos e unidades de medida (Ponto de Função) para acompanhar o que foi contratado e entregue.

```
          Navegador (next-app · Vue 3, SPA, mesma origem)
                         │  HTTPS /api/v1/**  (cookie de sessão HttpOnly + X-XSRF-TOKEN)
                         ▼
      ┌──────────────────────────────────────────────┐
      │ next-bff  (Spring Boot, porta 8080)          │  único componente exposto (R2)
      │  sessão · CSRF · CSP · RBAC por funcionalidade│
      │  trilha de acesso LGPD · exportações assínc. │──► banco NEXT_AUDIT
      │  limite de taxa · tradução de erros          │
      └──────────────────────────────────────────────┘
                         │  REST interno /internal/v1/**
                         │  X-Next-Internal-Token (JWT HS256, 60 s) + X-Correlation-Id
                         ▼
      ┌──────────────────────────────────────────────┐
      │ next-configuracao (Spring Boot, porta 8081)  │  rede interna
      │  api → application → domain ← infrastructure │──► banco NEXT_CONFIGURACAO
      │  autorização fina (perfil × dado)            │
      │  outbox de eventos · histórico imutável       │──► (Redis Streams — pendente)
      └──────────────────────────────────────────────┘
             │ porta PortfolioGateway      │ porta CoreGateway
             ▼                             ▼
      API do Portfólio (SUBTI)       next-core (usuários/perfis)
      [adaptador HTTP + demo]        [adaptador demo — pendente]
```

As três camadas da arquitetura (Front-end, BFF, Serviços) foram respeitadas: o navegador só fala com o BFF; o BFF só fala com o módulo; o módulo só acessa o próprio banco.

## 1.2 Decisões tomadas neste módulo

| Nº | Decisão | Justificativa |
|---|---|---|
| D46 (aplicada) | Front-end em **Vue 3 + TypeScript estrito + Vite** | Atende aos critérios da seção 4.1 (componentes, CSS custom properties, CSP sem `unsafe-inline`/`unsafe-eval`, build estático) e ao `Tecnologia.txt`. |
| D47 (aplicada) | Sessão no servidor com cookie HttpOnly; nenhum token no navegador | Seção 8.1. Cookie `NEXT_SESSION` (produção: `__Host-next_session`, `Secure`). |
| D48 (alterada) | **Oracle** como motor (não PostgreSQL) | `Tecnologia.txt` e DOD (padrão SEMEF/SUBTI = Oracle 19). SQL escrito compatível com 19c; CI valida em Oracle 23ai Free. |
| D49 (parcial) | Transactional outbox | Tabela `evento_outbox` gravada na mesma transação; publicador padrão em log. Redis Streams é integração pendente. |
| D50 (aplicada) | Problem Details (RFC 9457) em todas as camadas | `type`, `title`, `status`, `detail`, `instance`, `correlationId`, `errors[]` por campo. |
| D51 (aplicada) | Tokens de "tinta" (`--nx-*-ink`) e `--nx-on-accent` | Contraste WCAG AA para texto colorido. |
| ADR-C1 | Monorepo com `backend/` (Maven multi-módulo), `frontend/` e `infra/` | Facilita a replicação como produto (um clone, um build). A separação lógica dos repositórios da seção 11.1 é mantida por módulos Maven independentes. |
| ADR-C2 | Fase 1 de empacotamento: BFF e módulo em **processos separados** desde o início | Seção 9.3; extração futura sem mudar contratos. |
| ADR-C3 | Identificadores UUID armazenados como `VARCHAR2(36)` | Portável entre Oracle 19c/23ai e H2; legível em consultas de auditoria. |
| ADR-C4 | Booleanos como `NUMBER(1)` (conversor JPA) | Oracle 19c não tem tipo BOOLEAN em tabela. |
| ADR-C5 | Roteamento do front com hash (`#/`) | O mesmo build roda pelo BFF, por proxy reverso ou pelo GitHub Pages sem regra de reescrita. |
| ADR-C6 | **Modo demonstração**: BFF simulado no navegador (TypeScript) | Permite avaliar a ferramenta no GitHub Pages, sem servidor. Reproduz as regras RN01–RN29; a fonte da verdade continua sendo o Java. |
| ADR-C7 | Entidades JPA no pacote `domain.model`; regras puras em `domain.regra` | Pragmatismo: as regras de negócio críticas (CNPJ, vigência, valores, calendário, SLA, notificação) ficam em classes sem dependência de framework, verificadas por ArchUnit e com cobertura ≥ 80% exigida no build. |
| ADR-C8 | Nomes técnicos em inglês nos sufixos (Service, Repository, Controller) e termos de negócio em português | Regra única para todo o código (seção 11.2). |

## 1.3 Visão lógica do módulo (hexagonal)

```
br.com.subti.next.configuracao
 ├─ api/                 Controllers REST internos (/internal/v1), TratadorErros (Problem Details), ETag/Location
 ├─ application/         Casos de uso transacionais, DTOs, histórico, outbox, idempotência, paginação
 ├─ domain/
 │   ├─ regra/           Regras puras: Cnpj, ContratoCalculos, Fiscalizacao, CalculadoraExpediente,
 │   │                   CalculoSla, SlaCicloVida, RegraNotificacao, Mascaras  (sem framework)
 │   ├─ model/           Entidades JPA, UsuarioAtual (identidade propagada), conversores
 │   ├─ repositorio/     Portas de persistência (Spring Data)
 │   ├─ porta/           PortfolioGateway, CoreGateway, PublicadorEventos
 │   └─ excecao/         Exceções de domínio tipadas (422, 404, 409, 403, 412, 428)
 └─ infrastructure/
     ├─ integracao/      Adaptadores Portfólio (HTTP e demo), Core (demo), carga de dados fictícios
     ├─ seguranca/       TokenInternoFilter (valida JWT do BFF, correlationId)
     └─ eventos/         Publicador (log), tarefas agendadas (outbox, encerramento de versões de SLA)
```

Regras de dependência verificadas no build (`ArquiteturaTest`, ArchUnit):
1. `domain.regra` só depende de `java.*`.
2. `domain` não depende de `api`, `application`, `infrastructure` nem de `spring-web`.
3. `application` não depende de `api` nem de `infrastructure`.
4. `api` não acessa `infrastructure` nem repositórios diretamente.
5. Nenhuma classe referencia pacotes de outros módulos (BFF, Portfólio, Sustentação, Engenharia).

### BFF (`br.com.subti.next.bff`)

| Pacote | Responsabilidade |
|---|---|
| `seguranca` | `SegurancaConfig` (Spring Security: sessão, CSRF SPA, CSP, HSTS, X-Frame-Options), `AuthController` (login de desenvolvimento + MFA simulado), `Rbac` (perfil × funcionalidade, seção 5.4 do DOD), `TokenInternoEmissor`, `LimiteTaxa` |
| `modulos` | `ConfiguracaoProxyController` (rotas `/api/v1/configuracao/**`), `ClienteModulo` (timeout, circuit breaker, token interno) |
| `auditoria` | `AcessoFilter` (duração absoluta de sessão, limite de taxa, trilha LGPD), `TrilhaRepositorio`, `ExportacaoServico` (CSV assíncrono, link temporário) |
| `web` | `Problema`, `TratadorErrosBff` |

### Front-end (`frontend/next-app`)

| Pasta | Conteúdo |
|---|---|
| `src/styles` | `next-tokens.css` (fonte única de tokens, seção 12.6) e `base.css` (componentes 12.7–12.10) |
| `src/api` | `cliente.ts` (HTTP único: CSRF, Problem Details, sessão expirada), `tipos.ts` (contrato), `demo/` (BFF simulado) |
| `src/componentes` | Casca (app bar, sidebar 56/240 px, bottom nav, FAB), Janela (modal acessível), Campo, Selo (badge), Estados, Paginação, Histórico, etc. |
| `src/views` | Uma tela por funcionalidade (contratos, contratadas, SLA, portfólio, cadastros de apoio, calendários, notificações, auditoria) |
| `src/lib` | `format.ts` (Intl pt-BR), `cnpj.ts` (mesma regra do servidor) |

## 1.4 Visão de dados

Esquema `NEXT_CONFIGURACAO` (migração `V1__modelo_configuracao.sql`):

| Necessidade | Tabelas |
|---|---|
| N01 Contrato | `contrato`, `contrato_item`, `contrato_fonte`, `contrato_sistema`, `contrato_aditivo` (imutável), `contrato_fiscal` |
| N02 Contratada | `contratada`, `contratada_membro`, `contratada_alteracao` (imutável, com BLOB do anexo) |
| N03 SLA | `sla`, `sla_servico`, `sla_sistema`, `sla_versao`, `sla_regra` |
| N04 Portfólio | `contrato_sistema`, `sla_sistema` (UUID do sistema sem FK + nome de exibição, RN19) |
| N05 Auditoria | `historico_alteracao` (imutável) + `trilha_acesso`/`exportacao` no esquema `NEXT_AUDIT` (BFF) |
| N06 Notificação | `notificacao_config` |
| N07, N08, N09 | `unidade_medida`, `fonte_recurso`, `servico` |
| N10 Calendário | `calendario`, `calendario_expediente`, `calendario_feriado` |
| Integração | `evento_outbox`, `evento_processado` (consumo idempotente), `chave_idempotencia` |

Convenções (seção 7.2): snake_case no singular; PK `id` UUID; colunas de controle `created_at`, `created_by`, `updated_at`, `updated_by`, `version` (bloqueio otimista); referências a outros módulos por UUID **sem** chave estrangeira; **nada é excluído** (RN04 — situação `ATIVO`/`DESATIVADO` com justificativa).

Registros imutáveis (somente inclusão): `contrato_aditivo`, `contratada_alteracao`, `historico_alteracao`, `trilha_acesso`. Em produção o usuário de aplicação recebe apenas `SELECT, INSERT` nessas tabelas (`infra/oracle/conceder-permissoes.sql`).

## 1.5 Comunicação e contratos

| Canal | Padrão |
|---|---|
| Navegador → BFF | REST JSON `/api/v1/**`, mesma origem, cookie de sessão, cabeçalho `X-XSRF-TOKEN` em toda escrita |
| BFF → módulo | REST `/internal/v1/**`, `X-Next-Internal-Token` (JWT HS256, emissor `next-bff`, audiência `next-configuracao`, validade 60 s), `X-Correlation-Id` |
| Concorrência | `ETag` em toda leitura de registro; `If-Match` obrigatório em `PUT` (428 sem cabeçalho, 412 se desatualizado) |
| Idempotência | `Idempotency-Key` em criação de contrato, aditivo e SLA (repetição devolve 200 + `Idempotent-Replayed: true`) |
| Paginação | `page`, `size` (máx. 100), `sort=campo,asc|desc` (lista branca); resposta com `totalElements` |
| Erros | Problem Details com `errors[{field,message}]` e `correlationId`; o BFF reescreve caminhos internos e oculta detalhes de 5xx |
| Eventos | `configuracao.<entidade>.<fato>` no outbox (ex.: `configuracao.sla.vigente`, `configuracao.aditivo.registrado`); consumo idempotente de `portfolio.sistema.*` em `POST /internal/v1/eventos` |
| OpenAPI | Gerado pelo springdoc em `/v3/api-docs` e `/swagger-ui.html` do módulo (rede interna) |

## 1.6 Segurança e LGPD

- **Autenticação**: login de desenvolvimento com usuários fictícios por perfil; **MFA obrigatório** para usuários de fábrica (código simulado). gov.br/SSO via OIDC no BFF é integração pendente (documento 2).
- **Sessão**: 30 min de inatividade e 10 h de duração absoluta; troca do id de sessão no login.
- **CSRF**: token sincronizado (cookie `XSRF-TOKEN` + cabeçalho).
- **Cabeçalhos**: CSP `default-src 'self'` sem `unsafe-inline`/`unsafe-eval`, `frame-ancestors 'none'`, HSTS, `nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.
- **Autorização em dois níveis** (RNF02): BFF (perfil × funcionalidade, tabela `Rbac`) e módulo (perfil × dado: o N2 gestor só enxerga a própria fábrica; resposta 404 para não revelar existência).
- **Limites de taxa**: login (10/min por IP), exportações (3/min por usuário), geral (600/min).
- **LGPD**: o módulo guarda apenas `usuario_id` e nome de exibição (RN10); CPF só na sessão do BFF e sempre mascarado (`***.456.789-**`); logs sem CPF/e-mail/conteúdo; trilha de acesso marca rotas com dados pessoais; dados de teste fictícios.
- **Uploads**: lista de tipos (PDF, PNG, JPEG), 5 MB, verificação da assinatura do conteúdo, nome de arquivo saneado. Antivírus é pendência (documento 2).

## 1.7 Implantação

| Ambiente | Como |
|---|---|
| Desenvolvimento | `scripts/iniciar-local.ps1` / `.sh` — H2 em modo Oracle, dados fictícios |
| Homologação/treinamento | `infra/docker-compose.yml` — Oracle Free, módulo e BFF em contêiner; só o BFF publica porta |
| Produção | Imagens `infra/docker/Dockerfile.*` (OCI), perfil `oracle`, segredos por variável/cofre, `infra/oracle/*.sql` para usuários e permissões |
| Demonstração | GitHub Pages (`.github/workflows/pages.yml`) com o BFF simulado |

Pipeline (`.github/workflows/ci.yml`): lint + testes + tipos do front → testes, ArchUnit e cobertura do back → **integração com Oracle real** (Flyway + teste de fumaça pelo BFF).

## 1.8 Observabilidade

- Logs com `correlationId` em todas as linhas (`%X{correlationId}`), sem dados pessoais.
- Spring Boot Actuator: `health` (liveness/readiness), `metrics`, `prometheus` — no BFF, `/actuator/**` (exceto health) é negado ao público.
- Trilha de auditoria ≠ log: é dado de negócio, imutável, consultável na tela Auditoria.

## 1.9 Rastreabilidade DOD → implementação

| Requisito | Onde |
|---|---|
| RF01 | `ContratoService.listar`, `ContratosView.vue` |
| RF02 | `ContratoService.criar/alterar`, `ContratoFormView.vue` |
| RF03 | `ContratoService.registrarAditivo`, `ContratoCalculos`, aba Aditivos |
| RF04 | `ContratoService.designar/substituir/encerrarFiscal`, `Fiscalizacao` |
| RF05 | `ContratoService.mudarSituacao` (justificativa obrigatória) |
| RF06 | `Cnpj`, `ContratadaService`, `FormContratada.vue` |
| RF07 | `ContratadaService.adicionarMembro/encerrarMembro` |
| RF08 | `ContratadaService.registrarAlteracao/baixarAnexo` |
| RF09 | `SlaService.criar/salvarRegras`, `EditorRegras.vue` |
| RF10 | `PortfolioService`, `PortfolioView.vue` |
| RF11 | `GovernancaService.pesquisar`, `AuditoriaController.acessos`, `AuditoriaView.vue` |
| RF12 | `ExportacaoServico` (assíncrono, link temporário) |
| RF13 | `NotificacaoService`, `RegraNotificacao`, `NotificacoesView.vue` |
| RF14 | `CadastroApoioService`, `CadastroApoioView.vue` |
| RF15 | `CalendarioService`, `CalculadoraExpediente`, `CalendarioFormView.vue` |
| RF16 | `HistoricoService`, `HistoricoLista.vue` |
| RF17 | `SlaService.enviarParaAvaliacao/registrarParecer/decidir`, `SlaView.vue` |
| RN01–RN29 | ver `docs/05-relatorio-testes.md` (matriz regra × teste) |
| RNF01–RNF13 | seções 1.5–1.8 deste documento e relatório de testes |
