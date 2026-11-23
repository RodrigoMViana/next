-- =============================================================================
-- Next - Módulo Configuração (N01 a N10)
-- Esquema próprio do módulo (NEXT_CONFIGURACAO). Compatível com Oracle 19c+.
-- Convenções (Arquitetura 7.2): snake_case no singular, PK "id" UUID (VARCHAR2(36)),
-- colunas de controle created_at/created_by/updated_at/updated_by/version.
-- Referências a outros módulos (Core, Portfólio) por UUID, sem chave estrangeira.
-- =============================================================================

-- N07 - Unidade de medida -------------------------------------------------------
CREATE TABLE unidade_medida (
    id                      VARCHAR2(36)   NOT NULL,
    sigla                   VARCHAR2(20)   NOT NULL,
    nome                    VARCHAR2(120)  NOT NULL,
    descricao               VARCHAR2(500),
    situacao                VARCHAR2(20)   NOT NULL,
    justificativa_situacao  VARCHAR2(500),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_unidade_medida PRIMARY KEY (id),
    CONSTRAINT uk_unidade_medida_sigla UNIQUE (sigla),
    CONSTRAINT ck_unidade_medida_situacao CHECK (situacao IN ('ATIVO', 'DESATIVADO'))
);

-- N08 - Fonte de recursos -------------------------------------------------------
CREATE TABLE fonte_recurso (
    id                      VARCHAR2(36)   NOT NULL,
    codigo                  VARCHAR2(30)   NOT NULL,
    nome                    VARCHAR2(200)  NOT NULL,
    descricao               VARCHAR2(500),
    situacao                VARCHAR2(20)   NOT NULL,
    justificativa_situacao  VARCHAR2(500),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_fonte_recurso PRIMARY KEY (id),
    CONSTRAINT uk_fonte_recurso_codigo UNIQUE (codigo),
    CONSTRAINT ck_fonte_recurso_situacao CHECK (situacao IN ('ATIVO', 'DESATIVADO'))
);

-- N09 - Serviço -----------------------------------------------------------------
CREATE TABLE servico (
    id                      VARCHAR2(36)   NOT NULL,
    codigo                  VARCHAR2(30)   NOT NULL,
    nome                    VARCHAR2(200)  NOT NULL,
    descricao               VARCHAR2(1000),
    tipo                    VARCHAR2(20)   NOT NULL,
    unidade_medida_id       VARCHAR2(36)   NOT NULL,
    situacao                VARCHAR2(20)   NOT NULL,
    justificativa_situacao  VARCHAR2(500),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_servico PRIMARY KEY (id),
    CONSTRAINT uk_servico_codigo UNIQUE (codigo),
    CONSTRAINT fk_servico_unidade FOREIGN KEY (unidade_medida_id) REFERENCES unidade_medida (id),
    CONSTRAINT ck_servico_tipo CHECK (tipo IN ('DESENVOLVIMENTO', 'MANUTENCAO', 'SUSTENTACAO')),
    CONSTRAINT ck_servico_situacao CHECK (situacao IN ('ATIVO', 'DESATIVADO'))
);

-- N10 - Calendário --------------------------------------------------------------
CREATE TABLE calendario (
    id                      VARCHAR2(36)   NOT NULL,
    nome                    VARCHAR2(120)  NOT NULL,
    descricao               VARCHAR2(500),
    fuso_horario            VARCHAR2(60)   NOT NULL,
    vigencia_inicio         DATE           NOT NULL,
    situacao                VARCHAR2(20)   NOT NULL,
    justificativa_situacao  VARCHAR2(500),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_calendario PRIMARY KEY (id),
    CONSTRAINT uk_calendario_nome UNIQUE (nome),
    CONSTRAINT ck_calendario_situacao CHECK (situacao IN ('ATIVO', 'DESATIVADO'))
);

CREATE TABLE calendario_expediente (
    id                      VARCHAR2(36)   NOT NULL,
    calendario_id           VARCHAR2(36)   NOT NULL,
    dia_semana              NUMBER(1)      NOT NULL,
    hora_inicio             VARCHAR2(5)    NOT NULL,
    hora_fim                VARCHAR2(5)    NOT NULL,
    CONSTRAINT pk_calendario_expediente PRIMARY KEY (id),
    CONSTRAINT fk_cal_expediente_calendario FOREIGN KEY (calendario_id) REFERENCES calendario (id),
    CONSTRAINT ck_cal_expediente_dia CHECK (dia_semana BETWEEN 1 AND 7)
);
CREATE INDEX ix_cal_expediente_calendario ON calendario_expediente (calendario_id);

CREATE TABLE calendario_feriado (
    id                      VARCHAR2(36)   NOT NULL,
    calendario_id           VARCHAR2(36)   NOT NULL,
    data_feriado            DATE           NOT NULL,
    descricao               VARCHAR2(200)  NOT NULL,
    abrangencia             VARCHAR2(20)   NOT NULL,
    recorrente_anual        NUMBER(1)      NOT NULL,
    CONSTRAINT pk_calendario_feriado PRIMARY KEY (id),
    CONSTRAINT fk_cal_feriado_calendario FOREIGN KEY (calendario_id) REFERENCES calendario (id),
    CONSTRAINT ck_cal_feriado_abrangencia CHECK (abrangencia IN ('NACIONAL', 'ESTADUAL', 'MUNICIPAL', 'PONTO_FACULTATIVO')),
    CONSTRAINT ck_cal_feriado_recorrente CHECK (recorrente_anual IN (0, 1))
);
CREATE INDEX ix_cal_feriado_calendario ON calendario_feriado (calendario_id);

-- N02 - Contratada --------------------------------------------------------------
CREATE TABLE contratada (
    id                      VARCHAR2(36)   NOT NULL,
    cnpj                    VARCHAR2(14)   NOT NULL,
    razao_social            VARCHAR2(200)  NOT NULL,
    nome_fantasia           VARCHAR2(200),
    situacao                VARCHAR2(20)   NOT NULL,
    justificativa_situacao  VARCHAR2(500),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_contratada PRIMARY KEY (id),
    CONSTRAINT uk_contratada_cnpj UNIQUE (cnpj),
    CONSTRAINT ck_contratada_cnpj CHECK (REGEXP_LIKE(cnpj, '^[A-Z0-9]{12}[0-9]{2}$')),
    CONSTRAINT ck_contratada_situacao CHECK (situacao IN ('ATIVO', 'DESATIVADO'))
);

-- Equipe da contratada: referência ao usuário do Core (RN10: só id e nome de exibição)
CREATE TABLE contratada_membro (
    id                      VARCHAR2(36)   NOT NULL,
    contratada_id           VARCHAR2(36)   NOT NULL,
    usuario_id              VARCHAR2(36)   NOT NULL,
    nome_exibicao           VARCHAR2(120)  NOT NULL,
    papel                   VARCHAR2(20)   NOT NULL,
    inicio                  DATE           NOT NULL,
    fim                     DATE,
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_contratada_membro PRIMARY KEY (id),
    CONSTRAINT fk_membro_contratada FOREIGN KEY (contratada_id) REFERENCES contratada (id),
    CONSTRAINT ck_membro_papel CHECK (papel IN ('N2_GESTOR', 'N2_TECNICO'))
);
CREATE INDEX ix_membro_contratada ON contratada_membro (contratada_id);
CREATE INDEX ix_membro_usuario ON contratada_membro (usuario_id);

-- Alterações contratuais da empresa (registro imutável - somente inclusão)
CREATE TABLE contratada_alteracao (
    id                      VARCHAR2(36)   NOT NULL,
    contratada_id           VARCHAR2(36)   NOT NULL,
    data_alteracao          DATE           NOT NULL,
    tipo                    VARCHAR2(40)   NOT NULL,
    descricao               VARCHAR2(2000) NOT NULL,
    anexo_nome              VARCHAR2(255),
    anexo_tipo              VARCHAR2(100),
    anexo_tamanho           NUMBER(12),
    anexo_conteudo          BLOB,
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    created_by_nome         VARCHAR2(120)  NOT NULL,
    CONSTRAINT pk_contratada_alteracao PRIMARY KEY (id),
    CONSTRAINT fk_alteracao_contratada FOREIGN KEY (contratada_id) REFERENCES contratada (id)
);
CREATE INDEX ix_alteracao_contratada ON contratada_alteracao (contratada_id);

-- N01 - Contrato ----------------------------------------------------------------
CREATE TABLE contrato (
    id                      VARCHAR2(36)   NOT NULL,
    numero                  VARCHAR2(30)   NOT NULL,
    objeto                  VARCHAR2(1000) NOT NULL,
    contratada_id           VARCHAR2(36)   NOT NULL,
    calendario_id           VARCHAR2(36)   NOT NULL,
    vigencia_inicio         DATE           NOT NULL,
    vigencia_fim            DATE           NOT NULL,
    situacao                VARCHAR2(20)   NOT NULL,
    justificativa_situacao  VARCHAR2(500),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_contrato PRIMARY KEY (id),
    CONSTRAINT uk_contrato_numero UNIQUE (numero),
    CONSTRAINT fk_contrato_contratada FOREIGN KEY (contratada_id) REFERENCES contratada (id),
    CONSTRAINT fk_contrato_calendario FOREIGN KEY (calendario_id) REFERENCES calendario (id),
    CONSTRAINT ck_contrato_vigencia CHECK (vigencia_fim >= vigencia_inicio),
    CONSTRAINT ck_contrato_situacao CHECK (situacao IN ('ATIVO', 'DESATIVADO'))
);
CREATE INDEX ix_contrato_contratada ON contrato (contratada_id);

CREATE TABLE contrato_item (
    id                      VARCHAR2(36)   NOT NULL,
    contrato_id             VARCHAR2(36)   NOT NULL,
    servico_id              VARCHAR2(36)   NOT NULL,
    unidade_medida_id       VARCHAR2(36)   NOT NULL,
    volume                  NUMBER(15, 2)  NOT NULL,
    valor_unitario          NUMBER(15, 2)  NOT NULL,
    valor_total             NUMBER(17, 2)  NOT NULL,
    CONSTRAINT pk_contrato_item PRIMARY KEY (id),
    CONSTRAINT fk_item_contrato FOREIGN KEY (contrato_id) REFERENCES contrato (id),
    CONSTRAINT fk_item_servico FOREIGN KEY (servico_id) REFERENCES servico (id),
    CONSTRAINT fk_item_unidade FOREIGN KEY (unidade_medida_id) REFERENCES unidade_medida (id),
    CONSTRAINT ck_item_volume CHECK (volume > 0),
    CONSTRAINT ck_item_valor CHECK (valor_unitario >= 0)
);
CREATE INDEX ix_item_contrato ON contrato_item (contrato_id);

CREATE TABLE contrato_fonte (
    contrato_id             VARCHAR2(36)   NOT NULL,
    fonte_recurso_id        VARCHAR2(36)   NOT NULL,
    CONSTRAINT pk_contrato_fonte PRIMARY KEY (contrato_id, fonte_recurso_id),
    CONSTRAINT fk_cfonte_contrato FOREIGN KEY (contrato_id) REFERENCES contrato (id),
    CONSTRAINT fk_cfonte_fonte FOREIGN KEY (fonte_recurso_id) REFERENCES fonte_recurso (id)
);

-- Vínculo com sistema do Portfólio (RN19: UUID sem FK + nome de exibição)
CREATE TABLE contrato_sistema (
    id                      VARCHAR2(36)   NOT NULL,
    contrato_id             VARCHAR2(36)   NOT NULL,
    sistema_id              VARCHAR2(36)   NOT NULL,
    sistema_nome            VARCHAR2(200)  NOT NULL,
    CONSTRAINT pk_contrato_sistema PRIMARY KEY (id),
    CONSTRAINT fk_csistema_contrato FOREIGN KEY (contrato_id) REFERENCES contrato (id),
    CONSTRAINT uk_contrato_sistema UNIQUE (contrato_id, sistema_id)
);

-- Aditivos (registro imutável - somente inclusão, RN05)
CREATE TABLE contrato_aditivo (
    id                      VARCHAR2(36)   NOT NULL,
    contrato_id             VARCHAR2(36)   NOT NULL,
    numero                  VARCHAR2(30)   NOT NULL,
    aditivo_prazo           NUMBER(1)      NOT NULL,
    aditivo_valor           NUMBER(1)      NOT NULL,
    nova_vigencia_fim       DATE,
    valor_acrescimo         NUMBER(17, 2),
    data_assinatura         DATE           NOT NULL,
    justificativa           VARCHAR2(2000) NOT NULL,
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    created_by_nome         VARCHAR2(120)  NOT NULL,
    CONSTRAINT pk_contrato_aditivo PRIMARY KEY (id),
    CONSTRAINT fk_aditivo_contrato FOREIGN KEY (contrato_id) REFERENCES contrato (id),
    CONSTRAINT uk_aditivo_numero UNIQUE (contrato_id, numero),
    CONSTRAINT ck_aditivo_tipo CHECK (aditivo_prazo = 1 OR aditivo_valor = 1)
);

-- Comissão de fiscalização (RN06)
CREATE TABLE contrato_fiscal (
    id                      VARCHAR2(36)   NOT NULL,
    contrato_id             VARCHAR2(36)   NOT NULL,
    usuario_id              VARCHAR2(36)   NOT NULL,
    nome_exibicao           VARCHAR2(120)  NOT NULL,
    tipo_fiscal             VARCHAR2(30)   NOT NULL,
    inicio                  DATE           NOT NULL,
    fim                     DATE,
    motivo_encerramento     VARCHAR2(500),
    substituido_por_id      VARCHAR2(36),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_contrato_fiscal PRIMARY KEY (id),
    CONSTRAINT fk_fiscal_contrato FOREIGN KEY (contrato_id) REFERENCES contrato (id),
    CONSTRAINT ck_fiscal_tipo CHECK (tipo_fiscal IN ('GESTOR_CONTRATO', 'FISCAL_TECNICO', 'FISCAL_ADMINISTRATIVO', 'FISCAL_REQUISITANTE'))
);
CREATE INDEX ix_fiscal_contrato ON contrato_fiscal (contrato_id);

-- N03 - SLA ---------------------------------------------------------------------
CREATE TABLE sla (
    id                      VARCHAR2(36)   NOT NULL,
    codigo                  VARCHAR2(30)   NOT NULL,
    nome                    VARCHAR2(200)  NOT NULL,
    tipo_responsavel        VARCHAR2(20)   NOT NULL,
    contrato_id             VARCHAR2(36),
    contratada_id           VARCHAR2(36),
    equipe_interna          VARCHAR2(200),
    situacao                VARCHAR2(20)   NOT NULL,
    justificativa_situacao  VARCHAR2(500),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_sla PRIMARY KEY (id),
    CONSTRAINT uk_sla_codigo UNIQUE (codigo),
    CONSTRAINT fk_sla_contrato FOREIGN KEY (contrato_id) REFERENCES contrato (id),
    CONSTRAINT ck_sla_responsavel CHECK (
        (tipo_responsavel = 'CONTRATADA' AND contrato_id IS NOT NULL)
        OR (tipo_responsavel = 'EQUIPE_INTERNA' AND equipe_interna IS NOT NULL)),
    CONSTRAINT ck_sla_situacao CHECK (situacao IN ('ATIVO', 'DESATIVADO'))
);

CREATE TABLE sla_servico (
    sla_id                  VARCHAR2(36)   NOT NULL,
    servico_id              VARCHAR2(36)   NOT NULL,
    CONSTRAINT pk_sla_servico PRIMARY KEY (sla_id, servico_id),
    CONSTRAINT fk_slaserv_sla FOREIGN KEY (sla_id) REFERENCES sla (id),
    CONSTRAINT fk_slaserv_servico FOREIGN KEY (servico_id) REFERENCES servico (id)
);

CREATE TABLE sla_sistema (
    id                      VARCHAR2(36)   NOT NULL,
    sla_id                  VARCHAR2(36)   NOT NULL,
    sistema_id              VARCHAR2(36)   NOT NULL,
    sistema_nome            VARCHAR2(200)  NOT NULL,
    CONSTRAINT pk_sla_sistema PRIMARY KEY (id),
    CONSTRAINT fk_slasis_sla FOREIGN KEY (sla_id) REFERENCES sla (id),
    CONSTRAINT uk_sla_sistema UNIQUE (sla_id, sistema_id)
);
CREATE INDEX ix_sla_sistema_sistema ON sla_sistema (sistema_id);

CREATE TABLE sla_versao (
    id                      VARCHAR2(36)   NOT NULL,
    sla_id                  VARCHAR2(36)   NOT NULL,
    numero                  NUMBER(5)      NOT NULL,
    situacao                VARCHAR2(20)   NOT NULL,
    vigencia_inicio         DATE,
    vigencia_fim            DATE,
    motivo_alteracao        VARCHAR2(1000),
    parecer_favoravel       NUMBER(1),
    parecer_texto           VARCHAR2(2000),
    parecer_por_id          VARCHAR2(36),
    parecer_por_nome        VARCHAR2(120),
    parecer_em              TIMESTAMP(6),
    decisao_observacao      VARCHAR2(2000),
    decisao_por_id          VARCHAR2(36),
    decisao_por_nome        VARCHAR2(120),
    decisao_em              TIMESTAMP(6),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_sla_versao PRIMARY KEY (id),
    CONSTRAINT fk_versao_sla FOREIGN KEY (sla_id) REFERENCES sla (id),
    CONSTRAINT uk_sla_versao_numero UNIQUE (sla_id, numero),
    CONSTRAINT ck_sla_versao_situacao CHECK (situacao IN ('RASCUNHO', 'EM_AVALIACAO', 'VIGENTE', 'ENCERRADA'))
);

CREATE TABLE sla_regra (
    id                      VARCHAR2(36)   NOT NULL,
    sla_versao_id           VARCHAR2(36)   NOT NULL,
    ordem                   NUMBER(5)      NOT NULL,
    classe                  VARCHAR2(100)  NOT NULL,
    indicador               VARCHAR2(200)  NOT NULL,
    meta_valor              NUMBER(12, 2)  NOT NULL,
    meta_unidade            VARCHAR2(20)   NOT NULL,
    forma_contagem          VARCHAR2(20)   NOT NULL,
    calendario_id           VARCHAR2(36),
    permite_pausa           NUMBER(1)      NOT NULL,
    observacao              VARCHAR2(500),
    CONSTRAINT pk_sla_regra PRIMARY KEY (id),
    CONSTRAINT fk_regra_versao FOREIGN KEY (sla_versao_id) REFERENCES sla_versao (id),
    CONSTRAINT fk_regra_calendario FOREIGN KEY (calendario_id) REFERENCES calendario (id),
    CONSTRAINT ck_regra_unidade CHECK (meta_unidade IN ('MINUTOS', 'HORAS', 'DIAS_UTEIS', 'DIAS_CORRIDOS', 'PERCENTUAL')),
    CONSTRAINT ck_regra_contagem CHECK (forma_contagem IN ('CORRIDO_24X7', 'HORARIO_COMERCIAL')),
    CONSTRAINT ck_regra_calendario CHECK (forma_contagem = 'CORRIDO_24X7' OR meta_unidade = 'PERCENTUAL' OR calendario_id IS NOT NULL)
);
CREATE INDEX ix_regra_versao ON sla_regra (sla_versao_id);

-- N06 - Notificação -------------------------------------------------------------
CREATE TABLE notificacao_config (
    id                      VARCHAR2(36)   NOT NULL,
    nome                    VARCHAR2(120)  NOT NULL,
    evento                  VARCHAR2(120)  NOT NULL,
    canal_app               NUMBER(1)      NOT NULL,
    canal_email             NUMBER(1)      NOT NULL,
    perfis                  VARCHAR2(500)  NOT NULL,
    assunto                 VARCHAR2(200)  NOT NULL,
    modelo                  VARCHAR2(4000) NOT NULL,
    limiar_percentual       NUMBER(3),
    situacao                VARCHAR2(20)   NOT NULL,
    justificativa_situacao  VARCHAR2(500),
    created_at              TIMESTAMP(6)   NOT NULL,
    created_by              VARCHAR2(36)   NOT NULL,
    updated_at              TIMESTAMP(6),
    updated_by              VARCHAR2(36),
    version                 NUMBER(10)     NOT NULL,
    CONSTRAINT pk_notificacao_config PRIMARY KEY (id),
    CONSTRAINT ck_notif_canal CHECK (canal_app = 1 OR canal_email = 1),
    CONSTRAINT ck_notif_limiar CHECK (limiar_percentual IS NULL OR limiar_percentual BETWEEN 1 AND 99),
    CONSTRAINT ck_notif_situacao CHECK (situacao IN ('ATIVO', 'DESATIVADO'))
);

-- RF16 / RNF04 - Histórico de alterações (registro imutável - somente inclusão) ------
CREATE TABLE historico_alteracao (
    id                      VARCHAR2(36)   NOT NULL,
    entidade                VARCHAR2(60)   NOT NULL,
    entidade_id             VARCHAR2(36)   NOT NULL,
    acao                    VARCHAR2(30)   NOT NULL,
    campo                   VARCHAR2(80),
    valor_anterior          VARCHAR2(4000),
    valor_novo              VARCHAR2(4000),
    justificativa           VARCHAR2(1000),
    usuario_id              VARCHAR2(36)   NOT NULL,
    usuario_nome            VARCHAR2(120)  NOT NULL,
    ocorrido_em             TIMESTAMP(6)   NOT NULL,
    correlation_id          VARCHAR2(64),
    CONSTRAINT pk_historico_alteracao PRIMARY KEY (id)
);
CREATE INDEX ix_historico_entidade ON historico_alteracao (entidade, entidade_id);
CREATE INDEX ix_historico_ocorrido ON historico_alteracao (ocorrido_em);
CREATE INDEX ix_historico_usuario ON historico_alteracao (usuario_id);

-- Eventos: transactional outbox (Arquitetura 6.3, D49) e deduplicação de consumo ----
CREATE TABLE evento_outbox (
    id                      VARCHAR2(36)   NOT NULL,
    tipo                    VARCHAR2(120)  NOT NULL,
    versao                  NUMBER(3)      NOT NULL,
    ocorrido_em             TIMESTAMP(6)   NOT NULL,
    correlation_id          VARCHAR2(64),
    payload                 CLOB           NOT NULL,
    publicado_em            TIMESTAMP(6),
    tentativas              NUMBER(5)      NOT NULL,
    CONSTRAINT pk_evento_outbox PRIMARY KEY (id)
);
CREATE INDEX ix_outbox_pendente ON evento_outbox (publicado_em, ocorrido_em);

CREATE TABLE evento_processado (
    id                      VARCHAR2(36)   NOT NULL,
    tipo                    VARCHAR2(120)  NOT NULL,
    processado_em           TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_evento_processado PRIMARY KEY (id)
);

-- RNF06 - Chaves de idempotência para criações críticas ----------------------------
CREATE TABLE chave_idempotencia (
    chave                   VARCHAR2(100)  NOT NULL,
    usuario_id              VARCHAR2(36)   NOT NULL,
    rota                    VARCHAR2(200)  NOT NULL,
    recurso_id              VARCHAR2(36)   NOT NULL,
    criado_em               TIMESTAMP(6)   NOT NULL,
    CONSTRAINT pk_chave_idempotencia PRIMARY KEY (chave, usuario_id)
);
