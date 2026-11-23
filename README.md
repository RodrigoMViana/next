# Next · Módulo Configuração

Ferramenta de Gestão de Demandas de Desenvolvimento e Manutenção de Software — **Módulo Configuração** (necessidades N01 a N10 do Complemento DOD v02): contratos, contratadas (CNPJ numérico e **alfanumérico**), SLA parametrizável e versionado, vínculo com o Portfólio, auditoria, notificações, unidades de medida, fontes de recursos, serviços e calendários.

**Demonstração (GitHub Pages):** https://rodrigomviana.github.io/next/ — dados fictícios, sem servidor (o BFF é simulado no navegador). Escolha um perfil na tela de entrada; usuários de fábrica usam o código MFA `123456`.

[![CI](https://github.com/RodrigoMViana/next/actions/workflows/ci.yml/badge.svg)](https://github.com/RodrigoMViana/next/actions/workflows/ci.yml)
[![Pages](https://github.com/RodrigoMViana/next/actions/workflows/pages.yml/badge.svg)](https://github.com/RodrigoMViana/next/actions/workflows/pages.yml)

## Arquitetura em uma linha

`next-app` (Vue 3 + TypeScript) → `next-bff` (Spring Boot: sessão HttpOnly, CSRF, CSP, RBAC, trilha LGPD) → `next-configuracao` (Spring Boot hexagonal, Oracle, Flyway, outbox) — Java 25 LTS, Spring Boot 4.2.

## Começar em 3 comandos (Windows)

```powershell
winget install EclipseAdoptium.Temurin.25.JDK   # se ainda não tiver o JDK 25
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
powershell -ExecutionPolicy Bypass -File scripts/iniciar-local.ps1
```
Abra http://localhost:8080. (Linux/macOS: `./scripts/build.sh` e `./scripts/iniciar-local.sh`.)

Com Oracle em contêiner: `cp infra/.env.exemplo infra/.env` e `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build`.

## Documentação

| # | Documento |
|---|---|
| 1 | [Documento de Arquitetura](docs/01-documento-arquitetura.md) |
| 2 | [Integrações Pendentes](docs/02-integracoes-pendentes.md) |
| 3 | [Manual do Usuário](docs/03-manual-usuario.md) |
| 4 | [Manual Técnico](docs/04-manual-tecnico.md) |
| 5 | [Relatório de Testes](docs/05-relatorio-testes.md) |
| 6 | [O que falta fazer (integrações e decisões do cliente)](docs/06-o-que-falta-fazer.md) |
| 7 | [Fluxo de Validação](docs/07-fluxo-validacao.md) |

## Estrutura

```
backend/   next-configuracao (módulo, :8081) e next-bff (:8080) — Maven Wrapper
frontend/  next-app (Vue 3, Vite, Vitest)
infra/     Dockerfiles, docker-compose com Oracle, scripts de banco
scripts/   build, execução local e teste de fumaça
docs/      documentação do produto
```

## Testes

| Suíte | Casos |
|---|---|
| Java — domínio, arquitetura, API do módulo, BFF | 61 |
| Front — Vitest | 27 |
| Ponta a ponta pelo BFF (`scripts/teste-fumaca.sh`) | 15 verificações |
| CI — Oracle real (Flyway + fumaça) | a cada push |

Dados de desenvolvimento, homologação e demonstração são **fictícios** (CNPJs e CPFs gerados apenas para teste).
