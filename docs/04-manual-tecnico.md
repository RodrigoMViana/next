# 4. Manual Técnico

Guia para instalar, configurar, operar, evoluir e **replicar** o Módulo Configuração do Next como produto.

## 4.1 Estrutura do repositório

```
next/
├─ backend/                     Maven multi-módulo (Java 25, Spring Boot 4.2)
│  ├─ pom.xml                   BOM comum (versões fixadas), Lombok, JaCoCo
│  ├─ mvnw, mvnw.cmd, .mvn/     Maven Wrapper (baixa o Maven 3.9.16 automaticamente)
│  ├─ next-configuracao/        Módulo de serviço (porta 8081)
│  └─ next-bff/                 Backend for Frontend (porta 8080) — embute o front em /static
├─ frontend/next-app/           Vue 3.5 + TypeScript + Vite 8 (Vitest, ESLint, oxlint)
├─ infra/
│  ├─ docker/                   Dockerfiles (multi-stage, usuário não root)
│  ├─ docker-compose.yml        Oracle Free + módulo + BFF (rede interna)
│  ├─ .env.exemplo              Variáveis do compose
│  └─ oracle/                   Scripts de usuários e permissões (19c+)
├─ scripts/                     build, execução local e teste de fumaça (PowerShell e Bash)
├─ .github/workflows/           CI (inclui Oracle real) e GitHub Pages (demonstração)
└─ docs/                        Esta documentação
```

## 4.2 Pré-requisitos

| Ferramenta | Versão | Instalação (Windows) |
|---|---|---|
| JDK | **25 (LTS)** | `winget install EclipseAdoptium.Temurin.25.JDK` |
| Node.js | 24 LTS (≥ 22.18) | `winget install OpenJS.NodeJS.LTS` |
| Maven | via wrapper (`backend/mvnw`) | — |
| Docker | opcional (Oracle e imagens) | Docker Desktop / Podman |
| Oracle | 19c ou superior | em produção; localmente o H2 em modo Oracle é usado |

> Os scripts PowerShell localizam automaticamente um JDK 25 (`scripts/java25.ps1`) mesmo que o `JAVA_HOME` da máquina aponte para outra versão.

## 4.3 Build

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1          # testes + build completo
powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -SemTestes
```
```bash
./scripts/build.sh            # Linux/macOS
```

O que acontece: `npm ci` → `vitest` → `vue-tsc` → `vite build` (gera `frontend/next-app/dist`) → `mvnw verify` (testes, ArchUnit, JaCoCo ≥ 80% em `domain.regra`) → jars em `backend/*/target/`. O `next-bff.jar` inclui o front.

Builds parciais:
```bash
cd frontend/next-app && npm run dev            # Vite em :5173 com proxy de /api para :8080
cd frontend/next-app && npx vite build --mode demo   # build do GitHub Pages (NEXT_OUT_DIR=dist-demo)
cd backend && ./mvnw -pl next-configuracao spring-boot:run
```

## 4.4 Execução local (sem Docker)

```powershell
powershell -ExecutionPolicy Bypass -File scripts/iniciar-local.ps1
```
Sobe o módulo (8081, H2 em memória em modo Oracle, carga de dados fictícios) e o BFF (8080). Acesse **http://localhost:8080**. Logs do módulo em `logs/`.

Desenvolvimento do front com recarga automática: deixe o script acima rodando e, em outro terminal, `cd frontend/next-app && npm run dev` → http://localhost:5173.

## 4.5 Execução com Oracle (Docker Compose)

```bash
cp infra/.env.exemplo infra/.env      # troque as senhas e o segredo
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```
- `oracle` (gvenzl/oracle-free 23ai) cria os usuários `NEXT_CONFIGURACAO` e `NEXT_AUDIT` (`infra/oracle/init`).
- O Flyway de cada serviço cria as tabelas no primeiro start.
- Apenas o BFF publica porta (8080); módulo e banco ficam em redes internas.

## 4.6 Configuração (variáveis de ambiente)

### next-configuracao
| Variável | Padrão | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` (H2) ou `oracle` |
| `NEXT_CONFIGURACAO_PORTA` | 8081 | Porta HTTP |
| `NEXT_DB_URL` | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` | JDBC Oracle (perfil `oracle`) |
| `NEXT_DB_USUARIO` / `NEXT_DB_SENHA` | `NEXT_CONFIGURACAO` / — | Credenciais |
| `NEXT_TOKEN_INTERNO_SEGREDO` | valor de desenvolvimento | Segredo HS256 compartilhado com o BFF (≥ 32 bytes). **Obrigatório trocar.** |
| `NEXT_DEMO_CARREGAR` | `true` (local) / `false` (oracle) | Carga de dados fictícios se o banco estiver vazio |
| `NEXT_PORTFOLIO_MODO` | `demo` | `demo` ou `http` |
| `NEXT_PORTFOLIO_URL` / `NEXT_PORTFOLIO_TOKEN` | — | API do Portfólio |

### next-bff
| Variável | Padrão | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` (H2) ou `oracle` |
| `NEXT_BFF_PORTA` | 8080 | Porta HTTP |
| `NEXT_CONFIGURACAO_URL` | `http://localhost:8081` | Endereço interno do módulo |
| `NEXT_TOKEN_INTERNO_SEGREDO` | — | Mesmo segredo do módulo |
| `NEXT_AUTH_MODO` | `dev` | `dev` (usuários fictícios) ou `oidc` (gov.br — pendente) |
| `NEXT_DEV_MFA` | `123456` | Código de MFA simulado (somente `dev`) |
| `NEXT_SESSION_COOKIE` | `NEXT_SESSION` | Em produção: `__Host-next_session` |
| `NEXT_COOKIE_SECURE` | `false` | `true` atrás de HTTPS |
| `NEXT_AUDIT_DB_URL` / `_USUARIO` / `_SENHA` | — | Banco `NEXT_AUDIT` (perfil `oracle`) |
| `NEXT_APP_DIST` | `classpath:/static/` | Pasta alternativa do build do front (ex.: `file:/app/static/`) |

## 4.7 Banco de dados Oracle (produção)

1. Como DBA: `infra/oracle/criar-esquemas.sql` (usuários dono e aplicação por esquema).
2. Primeira implantação com o usuário **dono** (Flyway cria as tabelas): rode o serviço com `NEXT_DB_USUARIO=NEXT_CONFIGURACAO`.
3. `infra/oracle/conceder-permissoes.sql`: concede ao usuário de aplicação `SELECT/INSERT` nas tabelas imutáveis e `SELECT/INSERT/UPDATE` nas demais (sem `DELETE`, exceto coleções filhas substituídas na edição).
4. Para rodar com o usuário de aplicação, configure `spring.jpa.properties.hibernate.default_schema=NEXT_CONFIGURACAO` e `spring.flyway.enabled=false` (migrações apenas na implantação, com o dono).

Migrações: `backend/next-configuracao/src/main/resources/db/migration` e `backend/next-bff/src/main/resources/db/migration`. Regra: **expandir e depois contrair** (compatibilidade com a versão anterior para permitir rollback).

## 4.8 API

- **BFF (pública)**: `/api/v1/auth/{csrf,usuarios-demo,login,sessao,logout}`, `/api/v1/configuracao/**` (repasse ao módulo com RBAC), `/api/v1/auditoria/{acessos,exportacoes}`.
- **Módulo (interna)**: `/internal/v1/**`. OpenAPI 3.1 em `http://localhost:8081/v3/api-docs` e Swagger UI em `/swagger-ui.html` (as chamadas exigem `X-Next-Internal-Token`).

Exemplo de chamada interna (diagnóstico), com token gerado pelo BFF de testes:
```bash
curl -H "X-Next-Internal-Token: <jwt>" http://localhost:8081/internal/v1/painel
```

## 4.9 Como adicionar uma funcionalidade (padrão do projeto)

1. **Regra pura** em `domain/regra` (sem Spring) + teste unitário (a cobertura do pacote é exigida no build).
2. **Entidade/tabela**: nova migração `V{n}__descricao.sql` (Oracle 19c compatível; `VARCHAR2`, `NUMBER(1)` para booleanos, `TIMESTAMP(6)`); entidade em `domain/model` estendendo `CadastroComSituacao` (cadastros) ou `RegistroNovo` (somente inclusão).
3. **Caso de uso** em `application` (`@Transactional`, `Suporte.exigirGestao()`, `HistoricoService`, `OutboxService`).
4. **Controller** em `api` (ETag/`If-Match`, `Idempotency-Key` quando criar registro crítico).
5. **RBAC** no BFF: regra em `Rbac.REGRAS`.
6. **Front**: tipos em `api/tipos.ts`, tela em `views/`, rota em `router/index.ts`, item de menu em `Casca.vue`.
7. **Modo demonstração**: reproduza a rota em `api/demo/bffDemo.ts` (e um teste em `__tests__/bffDemo.spec.ts`).
8. Atualize `docs/` e o teste de fumaça, se for fluxo crítico.

## 4.10 Replicar para outro órgão (produto)

1. *Fork*/cópia do repositório.
2. Ajuste a carga fictícia (`CargaDemonstracao.java` e `semear()` do `bffDemo.ts`) ou desligue-a (`NEXT_DEMO_CARREGAR=false`).
3. Defina segredos e banco (seções 4.6 e 4.7) e o modo de autenticação.
4. Identidade visual: altere somente `frontend/next-app/src/styles/next-tokens.css` (paleta e tokens semânticos) e o `public/favicon.svg`. Nenhum componente usa cor fixa.
5. Publique as imagens (`infra/docker`) no registro do órgão e implante (Kubernetes ou equivalente).
6. Para uma vitrine estática, ative o GitHub Pages com *Source: GitHub Actions*; o workflow `pages.yml` publica o modo demonstração.

## 4.11 Operação

| Tarefa | Como |
|---|---|
| Saúde | `GET /actuator/health` (BFF), `/actuator/health/{liveness,readiness}` |
| Métricas | `/actuator/prometheus` (rede interna) |
| Rastrear erro relatado | Pegue o `correlationId` (ex.: `c-1a2b3c4d`) da mensagem e procure nos logs do BFF e do módulo |
| Eventos não publicados | Tabela `evento_outbox` com `publicado_em` nulo; após 10 tentativas ficam retidos para reprocessamento manual |
| Versões de SLA vencidas | Tarefa diária (00:05, America/Sao_Paulo) encerra versões cujo fim de vigência passou |
| Exportações | Tabela `exportacao` (BFF); arquivos expiram em 24 h |

## 4.12 Testes

```bash
cd backend && ./mvnw verify                          # 61 testes Java + ArchUnit + JaCoCo
cd frontend/next-app && npx vitest run               # 27 testes
cd frontend/next-app && npx oxlint . && npx eslint . # lint
BASE=http://localhost:8080 ./scripts/teste-fumaca.sh # 15 verificações ponta a ponta
```
Relatórios: `backend/next-configuracao/target/site/jacoco/index.html` e `backend/*/target/surefire-reports`.

## 4.13 Problemas conhecidos e soluções

| Sintoma | Causa | Solução |
|---|---|---|
| `UnsupportedClassVersionError (class file 69)` | JDK < 25 | Use os scripts (selecionam JDK 25) ou ajuste o `JAVA_HOME` |
| Porta 8080 ocupada pelo módulo | Variável genérica `PORT` | O projeto usa `NEXT_CONFIGURACAO_PORTA` e `NEXT_BFF_PORTA` |
| 403 em escrita | Faltou o cabeçalho anti-CSRF | O cliente lê o cookie `XSRF-TOKEN`; chame `GET /api/v1/auth/csrf` antes |
| 428 / 412 em `PUT` | Sem `If-Match` / registro alterado | Envie a ETag recebida no `GET` |
| 503 "Módulo indisponível" | Módulo fora do ar ou circuito aberto (5 falhas → 30 s) | Verifique o módulo; o BFF tenta de novo após 30 s |
