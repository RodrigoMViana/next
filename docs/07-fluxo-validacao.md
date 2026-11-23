# 7. Fluxo de Validação

Roteiro para validar **tudo** o que foi entregue. Pode ser executado no **GitHub Pages** (demonstração, sem instalar nada) ou no **ambiente local/homologação** (BFF + módulo Java + banco). Marque cada item.

```
 ┌───────────┐   ┌──────────────┐   ┌───────────┐   ┌──────────┐   ┌──────────┐   ┌───────────┐
 │ 0. Acesso │ → │ 1. Cadastros │ → │ 2. Contra-│ → │ 3. Con-  │ → │ 4. SLA e │ → │ 5. Gover- │
 │ e perfis  │   │   de apoio   │   │   tadas   │   │  tratos  │   │ Portfólio│   │  nança    │
 └───────────┘   └──────────────┘   └───────────┘   └──────────┘   └──────────┘   └───────────┘
                                                                                        │
                                       6. Técnico (API, segurança, testes) ◄────────────┘
```

**Onde testar**
- Demonstração: `https://rodrigomviana.github.io/next/` (dados no navegador; avatar → *Restaurar dados fictícios* para recomeçar).
- Local: `scripts/build.ps1` e `scripts/iniciar-local.ps1` → `http://localhost:8080`.

---

## Etapa 0 — Acesso e perfis (≈ 5 min)

| # | Passo | Resultado esperado | ✔ |
|---|---|---|---|
| 0.1 | Abrir a ferramenta | Tela "Entrar como" com 7 perfis fictícios | ☐ |
| 0.2 | Entrar como **Fábio Gestor (Fábrica Alfa)** sem código | Erro "MFA obrigatório" no campo | ☐ |
| 0.3 | Informar `000000` | "Código MFA inválido" | ☐ |
| 0.4 | Informar `123456` | Entra; painel mostra só dados da Fábrica Alfa | ☐ |
| 0.5 | Menu Contratos | Só contratos da Fábrica Alfa; sem botão "Novo contrato" | ☐ |
| 0.6 | Avatar → Sair; entrar como **Tânia Técnica** (123456) | Tela "Sem acesso a esta funcionalidade" | ☐ |
| 0.7 | Sair; entrar como **Nina Atendente N1** | Vê cadastros e Portfólio; sem botões de cadastro; sem Notificações/Auditoria no menu | ☐ |
| 0.8 | Sair; entrar como **Gustavo Gestor de TI** | Menu completo, exceto "Simular evento" (só Administrador) | ☐ |

## Etapa 1 — Cadastros de apoio (≈ 10 min) — como Gestor de TI

| # | Passo | Resultado esperado | ✔ |
|---|---|---|---|
| 1.1 | Calendários → **Novo calendário** "Teste" com expediente seg–sex e um feriado anual 25/12 | Salvo; lista mostra 40 h semanais | ☐ |
| 1.2 | Abrir o calendário → *Simular prazo*: início segunda 10:00, 4 horas, horário comercial | Prazo na mesma segunda 16:00 (almoço excluído) | ☐ |
| 1.3 | Editar o calendário com "Vale a partir de" no passado | Erro: alteração vale de hoje ou data futura (RN18) | ☐ |
| 1.4 | Unidades de medida → nova "UST2" | Salva; aparece na lista | ☐ |
| 1.5 | Desativar a unidade **PF** sem justificativa | Erro: em uso, informe a justificativa (RN15) | ☐ |
| 1.6 | Serviços → novo serviço tipo Manutenção, unidade UST2 | Salvo | ☐ |
| 1.7 | Fontes de recursos → nova; depois Histórico | Histórico mostra a criação com usuário e data | ☐ |

## Etapa 2 — Contratadas (≈ 10 min)

| # | Passo | Resultado esperado | ✔ |
|---|---|---|---|
| 2.1 | **Nova contratada** com CNPJ `12.ABC.345/01DE-36` | Erro "Dígitos verificadores do CNPJ não conferem" | ☐ |
| 2.2 | Colar `a1.b2c.3d4/e5f6-68` (minúsculas) | Máscara aplicada em maiúsculas; selo "CNPJ alfanumérico válido"; salva | ☐ |
| 2.3 | Tentar cadastrar o mesmo CNPJ de novo | Erro "CNPJ já cadastrado" (409) | ☐ |
| 2.4 | CNPJ numérico `11.222.333/0001-81` | Aceito (RN26) | ☐ |
| 2.5 | Na nova contratada: *Incluir membro* Tânia (N2+ técnico, início hoje) | Erro "Usuário já vinculado" — ela está na Fábrica Alfa no mesmo período | ☐ |
| 2.5b | Fábrica Alfa → Equipe → *Encerrar período* da Tânia com a data de hoje; volte e inclua-a na nova contratada com início amanhã | Inclusão aceita, com selo MFA | ☐ |
| 2.6 | *Registrar alteração* com anexo PDF | Aparece na linha do tempo com o anexo para baixar | ☐ |
| 2.7 | Editar a razão social → aba Histórico | Linha "razaoSocial: valor anterior → novo" | ☐ |

## Etapa 3 — Contratos (≈ 15 min)

| # | Passo | Resultado esperado | ✔ |
|---|---|---|---|
| 3.1 | Contratos → filtros (situação, contratada, "vigente em"), ordenar por vigência | Lista paginada e ordenada | ☐ |
| 3.2 | **Novo contrato** com fim antes do início | Erro no campo "Fim da vigência" (RN01) | ☐ |
| 3.3 | Corrigir; 2 itens; sem fiscal | Erro "Designe ao menos um fiscal" (RN06) | ☐ |
| 3.4 | Incluir fiscal e salvar | Detalhe mostra valor original = soma dos itens (RN02) | ☐ |
| 3.5 | *Registrar aditivo* de prazo + valor | Fim original preservado; fim efetivo e valor efetivo recalculados (RN05) | ☐ |
| 3.6 | Aditivo de supressão maior que o valor do contrato | Erro: valor não pode ficar negativo | ☐ |
| 3.7 | Fiscalização → *Encerrar* o único fiscal | Erro "Contrato ficaria sem fiscal" | ☐ |
| 3.8 | *Substituir* o fiscal | Anterior encerrado na véspera; novo vigente; histórico registra | ☐ |
| 3.9 | Abrir a edição do mesmo contrato em duas abas; salvar na primeira e depois na segunda | Segunda recebe "Registro alterado por outra pessoa" (If-Match) | ☐ |
| 3.10 | *Desativar* sem justificativa → com justificativa | Bloqueia sem justificativa; depois situação "Desativado" | ☐ |

## Etapa 4 — SLA e Portfólio (≈ 15 min)

| # | Passo | Resultado esperado | ✔ |
|---|---|---|---|
| 4.1 | SLA → **SLA-ALFA** | Versão 1 vigente, parecer favorável, decisão registrada | ☐ |
| 4.2 | *Nova versão* (motivo) → *Editar regras*: regra em horário comercial sem calendário | Erro "exige calendário" (RN13) | ☐ |
| 4.3 | Corrigir e *Enviar para avaliação* | Situação "Em avaliação"; painel mostra pendência | ☐ |
| 4.4 | Como Gestor: *Decidir vigência* antes do parecer | Erro "Aguarde o parecer" | ☐ |
| 4.5 | Entrar como **Bruno (Fábrica Beta)** e abrir SLA-ALFA | Não encontra (dados de outra fábrica) | ☐ |
| 4.6 | Entrar como **Fábio (Alfa)** → *Registrar parecer* favorável | Parecer gravado com nome e data | ☐ |
| 4.7 | Gestor → *Decidir vigência* com data passada | Erro "não pode ser retroativa" (RN14) | ☐ |
| 4.8 | Aprovar com data de amanhã | Versão 2 "Vigente" a partir de amanhã; versão 1 até hoje | ☐ |
| 4.9 | Aba *Simulador*: regra "Prioridade crítica · Tempo de solução" (4 horas), abertura 08:00 e consulta 11:30 do mesmo **dia útil**, limiar 80 | "Em risco"; com 1 h de pausa o prazo se desloca | ☐ |
| 4.10 | Novo SLA de **equipe interna** | Salvo sem contrato | ☐ |
| 4.11 | Portfólio → *Somente sistemas sem SLA vigente* | Lista PROT, PCID, FOLHA, ALMOX (RN20) | ☐ |
| 4.12 | Como **Ana Administradora**: *Simular evento* com novo nome para SIARR; reenviar o mesmo id | 1º processa e renomeia vínculos (RN19); 2º "ignorado (idempotência)" | ☐ |

## Etapa 5 — Notificações e Auditoria (≈ 10 min)

| # | Passo | Resultado esperado | ✔ |
|---|---|---|---|
| 5.1 | Notificações → nova, evento `sustentacao.sla.violado`, canal e-mail, modelo com `{{usuario.cpf}}` | Erro: e-mail não pode exibir CPF (RN21) | ☐ |
| 5.2 | Evento `sustentacao.sla.em-risco` sem limiar | Erro de limiar (RN22) | ☐ |
| 5.3 | Modelo válido → *Pré-visualizar* → Salvar | Mensagem com dados de exemplo; salva | ☐ |
| 5.4 | Auditoria → Governança: filtrar ação `ADITIVO` | Mostra o aditivo da etapa 3 com usuário e detalhe | ☐ |
| 5.5 | Acessos (LGPD): filtrar "Somente rotas com dados pessoais" | Inclui logins e consultas de auditoria (RN23) | ☐ |
| 5.6 | *Exportar* | Aviso de processamento → "Arquivo pronto"; aba Exportações → *Baixar CSV* | ☐ |
| 5.7 | Entrar como N1 e abrir `#/auditoria` | "Sem acesso" | ☐ |

## Etapa 6 — Validação técnica (ambiente local/homologação)

| # | Passo | Resultado esperado | ✔ |
|---|---|---|---|
| 6.1 | `scripts/build.ps1` | Todos os testes passam; `BUILD SUCCESS`; cobertura de regras ≥ 80% | ☐ |
| 6.2 | `scripts/iniciar-local.ps1` + `scripts/teste-fumaca.sh` | "Todas as verificações passaram" (15) | ☐ |
| 6.3 | `curl -I http://localhost:8080/` | `Content-Security-Policy` sem `unsafe-inline`, `X-Frame-Options: DENY`, `nosniff` | ☐ |
| 6.4 | `POST /api/v1/configuracao/contratos` sem cabeçalho `X-XSRF-TOKEN` | 403 | ☐ |
| 6.5 | `curl http://localhost:8081/internal/v1/painel` sem token | 401 (módulo não aceita chamadas fora do BFF) | ☐ |
| 6.6 | Erro de validação qualquer | Corpo Problem Details com `correlationId` e `errors[]`; mesmo id no log | ☐ |
| 6.7 | Parar o módulo e acessar uma tela | Mensagem "Módulo indisponível" (503), sem detalhes técnicos | ☐ |
| 6.8 | `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build` | Oracle + módulo + BFF no ar; Flyway aplica migrações | ☐ |
| 6.9 | GitHub → Actions → CI | Jobs Front-end, Back-end e **Integração com Oracle** verdes | ☐ |
| 6.10 | Responsivo: DevTools 375 px | Bottom nav, FAB e tabelas em cartões | ☐ |

## Critério de aceite

Todas as etapas 0–5 marcadas no ambiente de demonstração **e** 6.1–6.7 no ambiente local. Itens dependentes de integrações pendentes (documento 2) e decisões do cliente (documento 6) ficam registrados como ressalvas.
