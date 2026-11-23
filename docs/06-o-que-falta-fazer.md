# 6. O que falta fazer

## 6.1 Decisões pendentes do cliente (SEMEF/SUBTI)

| # | Decisão | Contexto | Impacto se não decidir | Sugestão |
|---|---|---|---|---|
| C1 | **Motor de banco**: Oracle 19c (padrão SEMEF) × PostgreSQL (D48) | O DOD registra o conflito; `Tecnologia.txt` indica Oracle | Esta entrega usa **Oracle**. Mudar exige só trocar driver/dialeto e revisar tipos | Registrar D48 = Oracle 19c+ |
| C2 | Confirmar as **regras de negócio propostas** (RN01–RN29 marcadas "Proposta") | Premissa 6 do DOD | Regras implementadas como propostas podem precisar de ajuste | Validar em oficina com a área |
| C3 | **Formato do SLA** com a contratada | RN11: "o desenho final é definido com a contratada" | O modelo é parametrizável (classe, indicador, meta, contagem); só a carga inicial muda | Validar com a fábrica usando o simulador |
| C4 | **Prazo de retenção** de dados pessoais e da trilha de auditoria | RN24, seção 13.3 — definir com o encarregado de dados | Sem expurgo automático hoje | Definir prazo; criar rotina de expurgo/arquivamento |
| C5 | **Tipos de fiscal** e **tipos de alteração contratual** | O DOD cita "tipo de fiscal" sem lista | Usadas listas sugeridas (Gestor do contrato, Fiscal técnico, administrativo, requisitante; alteração societária, razão social, endereço, representante, outra) | Confirmar listas |
| C6 | **Catálogo de eventos notificáveis** e variáveis de cada modelo | RN21/RN22 | Catálogo inicial com 10 eventos | Revisar com Sustentação/Core |
| C7 | Uppercase do CNPJ: **rejeitar** minúsculas (RN25) × **converter** (RN28) | As duas regras divergem | Implementado: a entrada converte para maiúsculas (RN28); o armazenamento só aceita maiúsculas (CHECK no banco) | Confirmar |
| C8 | **Ambiente de hospedagem** (data center × nuvem) e orquestrador | Premissa 2 | Imagens OCI prontas; manifests de Kubernetes não foram criados | Definir plataforma |
| C9 | Metas de **desempenho** e **usuários simultâneos** | Seção 13.3 | Sem teste de carga | Informar volume esperado |
| C10 | **Biblioteca de ícones** (seção 13.3) e cor por módulo | Pontos em aberto | Ícones SVG próprios no estilo Lucide; só o acento ciano no item ativo | Escolher biblioteca |
| C11 | **Paleta/identidade**: confirmar tokens D51 (tinta) | Proposta da arquitetura | Implementados | Confirmar |
| C12 | Desativação de **calendário em uso** | Não há regra explícita | Exige justificativa quando usado por contrato ativo | Confirmar |
| C13 | Aditivo de prazo que **encurta** a vigência | Não há regra explícita | Permitido (prevalece o aditivo mais recente), desde que não antes do início | Confirmar |
| C14 | Quem **encerra** a versão de SLA anterior | RN14 | Ao aprovar nova versão: anterior termina na véspera; tarefa diária encerra as vencidas | Confirmar |

## 6.2 Integrações (detalhe no documento 2)

- I1 gov.br + SSO (OIDC no BFF) e provedor de MFA.
- I2/I3 API e eventos do Portfólio (SUBTI).
- I4 next-core (usuários e perfis reais).
- I5 Redis Streams (eventos) e sessão distribuída.
- I6 Envio de e-mail das notificações (Core).
- I7 Consumo do SLA pela Sustentação.
- I8 Armazenamento de objetos para anexos; I9 antivírus; I10 cofre de segredos; I11 observabilidade central.

## 6.3 Itens técnicos para as próximas sprints

| Item | Prioridade |
|---|---|
| Validar migrações e testes em **Oracle 19c** de homologação | Alta |
| Implementar OIDC gov.br no BFF (I1) | Alta |
| Implementar `CoreGateway` e `PortfolioGateway` HTTP com testes de contrato (Pact/Spring Cloud Contract) | Alta |
| Publicador Redis Streams + consumidor de eventos do Portfólio; fila de erro com alerta | Média |
| Testes E2E com Playwright e acessibilidade com axe-core no pipeline | Média |
| Esquema com usuário dono × aplicação em produção (`hibernate.default_schema`) e scripts de permissão aplicados | Média |
| Manifests de implantação (Kubernetes/Helm), varredura de imagens, SAST/SCA (pipeline 9.4) | Média |
| Rotina de expurgo conforme prazo de retenção (C4) | Média |
| Notificação "contrato vencendo" agendada (evento `configuracao.contrato.vencendo`) | Baixa |
| Relatórios PDF/DOCX (JasperReports/POI, R5) — fora do escopo deste DOD | Baixa |
| Cópia de nome de exibição de usuários atualizada por evento do Core | Baixa |

## 6.4 O que já está pronto

N01 a N10 completos no back-end, no BFF e nas telas; versionamento e aprovação de SLA; histórico imutável; trilha LGPD e exportação assíncrona; CNPJ alfanumérico; modo demonstração no GitHub Pages; CI com Oracle real; documentação.
