# 3. Manual do Usuário — Módulo Configuração

Este manual explica, passo a passo, como usar as telas do Módulo Configuração do Next.

> **Ambiente de demonstração**: no GitHub Pages os dados são fictícios e ficam apenas no seu navegador. Para recomeçar, clique no seu avatar (canto superior direito) → **Restaurar dados fictícios**.

---

## 3.1 Entrar

1. Abra o endereço da ferramenta.
2. Escolha o perfil com que deseja entrar (nesta fase, usuários fictícios; o acesso definitivo será pelo **gov.br**).
3. Usuários de **fábrica** (N2 gestor e N2+ técnico) precisam informar o **código de autenticação (MFA)**. No ambiente de teste o código é **123456**.
4. A sessão expira após 30 minutos sem uso ou 10 horas de duração. Ao expirar, a tela de entrada reaparece com o aviso.

### Quem vê o quê

| Perfil | O que pode fazer neste módulo |
|---|---|
| Administrador | Tudo: cadastros, notificações, auditoria e a ferramenta "Simular evento do Portfólio" |
| Gestor de TI | Cadastros, decisão de vigência de SLA, notificações e auditoria |
| N1 | Consultar cadastros e o Portfólio |
| N2 gestor (fábrica) | Consultar **somente** os dados da própria fábrica e registrar o **parecer** sobre o SLA |
| N2+ técnico, Facilitador, Responsável da área, Membro de comitê, Cliente | Não acessam este módulo (veem a mensagem "Sem acesso") |

## 3.2 Navegação

- **Computador**: barra lateral com ícones à esquerda; o botão ☰ expande os nomes. A ação principal da tela fica no canto superior direito.
- **Celular**: barra inferior com Início, Contratos, Contratadas, SLA e **Mais**; a ação principal é o botão redondo (＋) acima da barra.
- O link "Pular para o conteúdo" aparece ao pressionar **Tab** — toda a ferramenta funciona por teclado.
- Situações sempre aparecem com **texto e ícone** (ex.: ✓ Ativo, ⚠ Em risco), nunca só com cor.

## 3.3 Início (painel)

Mostra os totais (contratos vigentes, contratadas, SLAs, sistemas sem SLA vigente…) e a lista de **pendências de configuração**:
- contrato terminando em até 60 dias;
- contrato sem fiscal vigente;
- versão de SLA aguardando parecer ou decisão;
- sistemas ativos no Portfólio sem SLA vigente.

Clique em **Ver** para ir direto ao registro. O cartão "Ordem de implantação" mostra a sequência recomendada para o primeiro uso.

## 3.4 Ordem recomendada de cadastro (primeiro uso)

1. **Calendário** → 2. **Unidade de medida** → 3. **Fonte de recursos** → 4. **Contratada e equipe** → 5. **Serviço** → 6. **Contrato** → 7. **SLA** → 8. **Vínculo com sistemas do Portfólio**. Notificações e Auditoria podem ser configuradas a qualquer momento.

## 3.5 Cadastros de apoio (Unidades de medida, Fontes de recursos, Serviços)

1. No menu, escolha o cadastro. Use **Buscar** e **Situação** para filtrar.
2. **Novo**: preencha sigla/código e nome (serviço também exige **tipo** e **unidade de medida**) e clique em **Salvar**.
3. **Editar**: altera os dados. Se outra pessoa alterou o registro antes, aparece "Registro alterado por outra pessoa" — recarregue e repita.
4. **Desativar/Reativar**: nada é excluído. Se o registro estiver em uso por contrato ou serviço ativo, a **justificativa** é obrigatória.
5. **Histórico**: mostra o que mudou, quem alterou, quando e o valor anterior.

## 3.6 Calendários

1. **Novo calendário**: informe nome, **fuso horário** e a data a partir da qual vale.
2. **Expediente**: para cada dia, ajuste os horários; use **+ Intervalo** para mais de um período no dia (ex.: 8h–12h e 14h–18h). Dias sem intervalo não contam no prazo.
3. **Feriados**: informe data, descrição, abrangência (nacional, estadual, municipal, ponto facultativo) e marque **Repete todo ano** quando for o caso.
4. Ao alterar um calendário existente, a nova regra vale a partir da data informada (hoje ou futura) e **não recalcula** chamados em andamento.
5. Aba **Simular prazo**: informe início, meta e unidade para ver quando o prazo termina.

## 3.7 Contratadas

1. **Nova contratada**: digite ou **cole** o CNPJ (com ou sem máscara). São aceitos o CNPJ **numérico** e o **alfanumérico** (letras A–Z nas 12 primeiras posições). Letras minúsculas são convertidas; o sistema confere os dígitos verificadores e mostra a mensagem no campo.
2. Na tela da contratada:
   - **Equipe**: *Incluir membro* (usuário, papel N2 gestor ou N2+ técnico, início e fim). MFA é obrigatório para esses usuários. *Encerrar período* registra a saída.
   - **Alterações contratuais**: registre data, tipo, descrição e anexe PDF/PNG/JPEG de até 5 MB. Os registros não podem ser alterados nem excluídos.
   - **Desativar**: se a contratada tem contrato vigente, a justificativa é obrigatória.

## 3.8 Contratos

### Consultar
Filtre por texto (número ou objeto), situação, contratada e "vigente em" (data). Clique no título da coluna para ordenar.

### Cadastrar
1. **Novo contrato** → informe número (único), contratada, objeto, vigência (o fim não pode ser anterior ao início) e o calendário que a contratada segue.
2. **Itens contratados**: serviço, volume e valor unitário. O valor total é calculado automaticamente.
3. Marque as **fontes de recursos** (ao menos uma) e os **sistemas do Portfólio** atendidos.
4. **Comissão de fiscalização**: ao menos um fiscal com designação vigente.
5. **Salvar**. Se sair da tela sem salvar, a ferramenta pede confirmação.

### Detalhe do contrato
- Cartões com **valor original** (itens) e **valor efetivo** (com aditivos), **fim da vigência original** e **efetiva**.
- **Aditivos** → *Registrar aditivo*: marque prazo e/ou valor, informe a nova data de fim e/ou o valor (negativo para supressão) e a justificativa. Os dados originais não mudam; os valores efetivos são recalculados.
- **Fiscalização**: *Designar fiscal*, *Substituir* (encerra a designação anterior na véspera do início da nova) e *Encerrar*. A ferramenta impede deixar o contrato sem fiscal vigente.
- **Sistemas**: vincular/desvincular sistemas do Portfólio.
- **Histórico**: todas as alterações e ações de governança.
- **Desativar/Reativar**: sempre com justificativa.

## 3.9 SLA

### Cadastrar
1. **Novo SLA** → código, nome e **responsável**: *Contratada* (escolha um contrato ativo e vigente) ou *Equipe interna*.
2. Marque os **serviços** monitorados (para contratada, apenas serviços que são itens do contrato) e os **sistemas** cobertos.
3. Inclua as **regras** (quantas forem necessárias): classe (ex.: Prioridade crítica), indicador (ex.: Tempo de solução), meta e unidade, forma de contagem (corrida 24x7 ou horário comercial com calendário) e se permite pausa.
4. **Salvar** cria a **versão 1 em rascunho**.

### Ciclo da versão
| Situação | Quem age | Ação |
|---|---|---|
| Rascunho | Gestor de TI / Administrador | *Editar regras* e *Enviar para avaliação* |
| Em avaliação | **N2 gestor da fábrica** do contrato | *Registrar parecer* (favorável ou desfavorável, com fundamentação) |
| Em avaliação | Gestor de TI / Administrador | *Decidir vigência*: aprovar com data de início (hoje ou futura) ou devolver para rascunho com motivo |
| Vigente | — | Para alterar, use *Nova versão* (copia as regras da versão anterior) |

Chamados abertos antes da nova vigência continuam com a versão anterior (sem efeito retroativo).

### Simulador de prazo
Aba **Simulador**: escolha a regra, a abertura do chamado, o momento da consulta, o limiar de alerta e, se quiser, uma pausa com motivo. O resultado mostra o prazo, o tempo consumido e a situação (**Em dia**, **Em risco** ou **Violado**).

## 3.10 Portfólio

Lista os sistemas do Portfólio com os contratos e SLAs vinculados. Marque **Somente sistemas sem SLA vigente** para ver as pendências de configuração. O Administrador pode usar **Simular evento do Portfólio** (ferramenta de homologação).

## 3.11 Notificações (Administrador e Gestor de TI)

1. **Nova notificação** → nome, **evento** (lista do catálogo), canais (na ferramenta e/ou e-mail) e **perfis destinatários**.
2. Para o evento *SLA em risco*, informe o **limiar** (% do prazo consumido).
3. Escreva o assunto e o modelo usando as **variáveis** do evento (clique nelas para inserir). Mensagens por e-mail não podem conter CPF nem conteúdo de chamados — a ferramenta bloqueia.
4. **Pré-visualizar** mostra a mensagem com dados de exemplo. **Salvar**.

## 3.12 Auditoria (Administrador e Gestor de TI)

- **Governança (alterações)**: filtre por recurso, ação e período; cada linha mostra quem, quando, o que mudou e o valor anterior.
- **Acessos (LGPD)**: quem acessou quais rotas, quando, de qual IP e se a rota envolve dados pessoais.
- **Exportar**: gera um arquivo CSV em segundo plano. Você é avisado quando ficar pronto; baixe na aba **Exportações**. O link vale por 24 horas e só para quem solicitou.
- O próprio acesso à auditoria é registrado.

## 3.13 Mensagens comuns

| Mensagem | O que fazer |
|---|---|
| "Registro alterado por outra pessoa" | Recarregue a página e refaça a alteração |
| "Informe a justificativa" | O registro está em uso; descreva o motivo |
| "Dígitos verificadores do CNPJ não conferem" | Confira o CNPJ digitado |
| "Sem acesso a esta funcionalidade" | Seu perfil não usa esta tela |
| Erro com "código c-xxxxxxxx" | Informe o código ao suporte: ele localiza o registro nos logs |
