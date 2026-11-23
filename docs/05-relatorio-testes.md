# 5. Relatório de Testes

| Item | Valor |
|---|---|
| Data da execução | 23/11/2026 |
| Versão | 1.0.0 |
| Ambiente | Windows 11, JDK Temurin 25.0.5, Node 24.21, H2 2.5 (modo Oracle); CI: Ubuntu + Oracle 23ai Free |
| Resultado geral | **103 casos automatizados aprovados, 0 falhas** + 15 verificações ponta a ponta + verificação manual no navegador |

## 5.1 Resumo

| Suíte | Ferramenta | Casos | Aprovados | Falhas |
|---|---|---|---|---|
| Regras de domínio (Java) — CNPJ, vigência/valores/aditivos, fiscais, calendário, SLA, notificação, máscara de CPF | JUnit 5 + AssertJ | 40 | 40 | 0 |
| Arquitetura (dependências entre camadas e módulos) | ArchUnit | 5 | 5 | 0 |
| Integração da API do módulo (H2 modo Oracle + Flyway + carga fictícia) | Spring Boot Test + MockMvc | 10 | 10 | 0 |
| BFF (sessão, CSRF, MFA, RBAC, degradação, trilha, exportação) | Spring Boot Test + Spring Security Test | 6 | 6 | 0 |
| Front-end — CNPJ, calendário/SLA (porte TS), BFF simulado | Vitest + jsdom | 27 | 27 | 0 |
| Checagem de tipos (TypeScript estrito) e lint | vue-tsc, oxlint, ESLint | — | sem erros | 0 |
| Ponta a ponta pelo BFF real (módulo + BFF rodando) | `scripts/teste-fumaca.sh` | 15 | 15 | 0 |
| **Total automatizado** | | **103** (+15 E2E) | **103** | **0** |

> O job **"Integração com Oracle"** do GitHub Actions executa as migrações Flyway e o mesmo teste de fumaça contra um Oracle real (23ai Free) a cada push. O resultado fica na aba *Actions* do repositório.

## 5.2 Cobertura de código (JaCoCo — módulo next-configuracao)

| Pacote | Linhas cobertas |
|---|---|
| `domain.regra` (regras de negócio — meta RNF12 ≥ 80%) | **303/304 = 99,7%** ✅ (verificado no build; abaixo de 80% o build falha) |
| `domain.model` | 281/301 = 93,4% |
| `domain.excecao` | 30/30 = 100% |
| `application` (casos de uso) | 927/1308 = 70,9% |
| `application.dto` | 130/135 = 96,3% |
| `infrastructure.seguranca` | 38/41 = 92,7% |
| `infrastructure.integracao` | 144/182 = 79,1% |
| `api` | 97/215 = 45,1% |

## 5.3 Matriz regra de negócio × teste

| Regra | Teste(s) | Resultado |
|---|---|---|
| RN01 vigência fim ≥ início; uso só na vigência efetiva | `RegrasContratoSlaNotificacaoTest.Contrato.rn01*`, `ApiIntegracaoTest.c01` (vigência invertida → 422), `d01` (decisão fora da vigência) | ✅ |
| RN02 valor do item e do contrato calculados | `rn02ValorDoItemEDoContrato`, `c01` (valorOriginal 1050) | ✅ |
| RN03 número do contrato único | `ContratoService` + `bffDemo.spec` | ✅ |
| RN04 nada é excluído (desativação) | `c01` (desativação → DESATIVADO), `h01` | ✅ |
| RN05 aditivos imutáveis, efetivos calculados | `rn05AditivosPreservamOriginaisECalculamEfetivos`, `c01`, `bffDemo.spec` | ✅ |
| RN06 ao menos um fiscal vigente; substituição registrada | `rn06Fiscalizacao`, `c01` (encerrar único → 422; substituir → 2 designações), `bffDemo.spec` | ✅ |
| RN07 CNPJ obrigatório, único e válido | `b01` (422, 409), `CnpjTest` | ✅ |
| RN08 desativar contratada com contrato vigente exige justificativa | `ContratadaService.mudarSituacao` (manual + demo) | ✅ |
| RN09 equipe N2 com MFA | `BffIntegracaoTest.mfa`, `bffDemo.spec` | ✅ |
| RN10 só id e nome de exibição | modelo de dados (`contratada_membro`, `contrato_fiscal`) | ✅ (inspeção) |
| RN11 SLA parametrizável (contratada ou equipe interna) | `d01`, carga fictícia (SLA-ALFA e SLA-N1) | ✅ |
| RN12 contagem corrida ou em expediente | `CalculadoraExpedienteTest`, `CalculoSlaTest.calculaPrazos`, `regras.spec.ts` | ✅ |
| RN13 horário comercial exige calendário; pausas com motivo descontadas | `validaCombinacoesDeRegra`, `descontaPausasDoTempoConsumido`, `d01` (422 sem calendário) | ✅ |
| RN14 versão nova sem efeito retroativo; ciclo Rascunho → Em avaliação → Vigente | `rn14*`, `d01` (versão 1 hoje, versão 2 amanhã) | ✅ |
| RN15 item em uso só desativa com justificativa | `h01` | ✅ |
| RN16 PF apurado pela Engenharia | fora do escopo do módulo (só o cadastro da unidade) | n/a |
| RN17 expediente com vários intervalos e feriados recorrentes | `CalculadoraExpedienteTest` | ✅ |
| RN18 alteração de calendário vale da vigência informada | `h01` (vigência passada → 422) | ✅ |
| RN19 vínculo por UUID + nome copiado e atualizado por evento | `e01` (evento renomeia), modelo de dados | ✅ |
| RN20 sistema ativo sem SLA vigente sinalizado | `a02`, `e01` | ✅ |
| RN21 padrão do evento; e-mail sem CPF/conteúdo | `rn21*`, `f01` | ✅ |
| RN22 limiar configurável no alerta de risco | `rn22LimiarSoNoAlertaDeRisco`, `avaliaSituacaoComLimiar`, `f01` | ✅ |
| RN23 consulta restrita, acesso registrado, CPF mascarado | `g01`, `BffIntegracaoTest.trilhaEExportacao`, `rn23CpfMascarado`, `mfa` (***.287.938-**) | ✅ |
| RN24 registros imutáveis; exportação assíncrona com link temporário | `trilhaEExportacao` (token inválido → 404; outro usuário → 404) | ✅ |
| RN25 formato 14 posições alfanuméricas + DV numérico | `CnpjTest.rejeitaInvalidos`, `cnpj.spec.ts` | ✅ |
| RN26 numérico continua válido | `CnpjTest.aceitaValidos` | ✅ |
| RN27 DV por ASCII−48 e módulo 11 | `calculaDigitos` (12ABC34501DE → 35; 112223330001 → 81) | ✅ |
| RN28 sem máscara, maiúsculas, unicidade sobre valor normalizado | `normalizaEFormata`, `b01` (minúsculas aceitas, duplicado → 409) | ✅ |
| RN29 sem consulta à Receita | por desenho | ✅ |

## 5.4 Requisitos não funcionais verificados

| RNF | Evidência | Resultado |
|---|---|---|
| RNF01 segurança (BFF, cookie HttpOnly, CSRF, limite de taxa, validação dupla) | `BffIntegracaoTest.csrfObrigatorio`, `semSessao` (cabeçalhos), teste de fumaça | ✅ |
| RNF02 autorização em dois níveis | `rbacEDegradacao`, `regrasRbac`, `c01` (N2 da Alfa → 404 no contrato da Beta) | ✅ |
| RNF03 MFA para fábrica | `mfa` | ✅ (gov.br pendente) |
| RNF04 histórico com quem/quando/valor anterior | `b01` (histórico de razão social) | ✅ |
| RNF05 LGPD (minimização, máscara) | `mfa`, inspeção do modelo e dos logs | ✅ |
| RNF06 ETag/If-Match e idempotência | `b01` (428, 412, ETag "1"), `c01` (Idempotent-Replayed) | ✅ |
| RNF07 paginação | `c01` (size/sort/totalElements) | ✅ (p95 não medido) |
| RNF08 Problem Details + correlationId + erro por campo | todos os testes de erro (`$.errors[0].field`, `$.correlationId`) | ✅ |
| RNF09 OpenAPI e eventos padronizados com outbox | springdoc `/v3/api-docs`; log "evento publicado tipo=configuracao.*" | ✅ (Redis pendente) |
| RNF10 acessibilidade | revisão manual: semântica, foco visível, teclado, `aria-*`, cor + texto | ⚠ parcial (axe-core pendente) |
| RNF11 mobile first, pt-BR | verificação visual 375 px e desktop; `format.ts` (Intl pt-BR) | ✅ |
| RNF12 cobertura ≥ 80% nas regras de domínio | JaCoCo 99,7% (regra no build) | ✅ |
| RNF13 CNPJ como texto de 14 posições; testes com e sem máscara e DV inválido | `CnpjTest`, `cnpj.spec.ts`, `b01`, CHECK no banco | ✅ |

## 5.5 Testes manuais no navegador

| Cenário | Resultado |
|---|---|
| Login por perfil; N2 com MFA; N2+ técnico e Cliente → "Sem acesso" | ✅ |
| Painel com KPIs e pendências (contrato vencendo, sistemas sem SLA) | ✅ |
| Detalhe do contrato: valores original × efetivo, aditivos, fiscais, sistemas, histórico | ✅ |
| Layout mobile (375 px): bottom nav, FAB, tabelas em cartões | ✅ |
| BFF real com CSP estrita: sem violações no console | ✅ |
| Modo demonstração (build estático) | ✅ |

## 5.6 Não testado nesta entrega (e por quê)

| Item | Motivo | Encaminhamento |
|---|---|---|
| Oracle **19c** | Ambiente local sem Docker; CI usa Oracle 23ai Free | Executar `infra/oracle` + Flyway em homologação 19c da SUBTI |
| `docker compose` completo | Docker indisponível na máquina de desenvolvimento | Validar em homologação |
| gov.br, Core, Portfólio reais, Redis, SMTP | Integrações pendentes (documento 2) | Testes de contrato quando as APIs existirem |
| Acessibilidade automatizada (axe-core) e E2E com Playwright | Fora do tempo desta entrega | Incluir no pipeline (seção 11.4 da arquitetura) |
| Desempenho (p95 < 500 ms), carga, DAST/pentest | Exige ambiente de homologação | Etapa 7 do pipeline (seção 9.4) |

## 5.7 Como reproduzir

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
powershell -ExecutionPolicy Bypass -File scripts/iniciar-local.ps1
# em outro terminal (Git Bash):
BASE=http://localhost:8080 ./scripts/teste-fumaca.sh
```
