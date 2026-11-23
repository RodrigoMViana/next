-- =============================================================================
-- Next - next-bff: banco next_audit (Arquitetura 7.1 e 7.3)
-- Trilha de acesso a dados pessoais (LGPD) e exportações assíncronas.
-- trilha_acesso é append-only: conceder apenas INSERT e SELECT ao usuário da aplicação.
-- =============================================================================
CREATE TABLE trilha_acesso (
    id                  VARCHAR2(36)   NOT NULL,
    ocorrido_em         TIMESTAMP(6)   NOT NULL,
    usuario_id          VARCHAR2(36),
    usuario_nome        VARCHAR2(120),
    perfis              VARCHAR2(200),
    metodo              VARCHAR2(10)   NOT NULL,
    rota                VARCHAR2(400)  NOT NULL,
    modulo              VARCHAR2(40)   NOT NULL,
    recurso             VARCHAR2(60),
    acao                VARCHAR2(20)   NOT NULL,
    status_http         NUMBER(3)      NOT NULL,
    ip                  VARCHAR2(45),
    correlation_id      VARCHAR2(64),
    dado_pessoal        NUMBER(1)      NOT NULL,
    duracao_ms          NUMBER(10),
    CONSTRAINT pk_trilha_acesso PRIMARY KEY (id)
);
CREATE INDEX ix_trilha_ocorrido ON trilha_acesso (ocorrido_em);
CREATE INDEX ix_trilha_usuario ON trilha_acesso (usuario_id);

CREATE TABLE exportacao (
    id                  VARCHAR2(36)   NOT NULL,
    solicitante_id      VARCHAR2(36)   NOT NULL,
    solicitante_nome    VARCHAR2(120)  NOT NULL,
    tipo                VARCHAR2(20)   NOT NULL,
    filtros             VARCHAR2(2000),
    situacao            VARCHAR2(20)   NOT NULL,
    criado_em           TIMESTAMP(6)   NOT NULL,
    concluido_em        TIMESTAMP(6),
    total_registros     NUMBER(10),
    arquivo             BLOB,
    token_hash          VARCHAR2(64),
    expira_em           TIMESTAMP(6),
    mensagem_erro       VARCHAR2(500),
    CONSTRAINT pk_exportacao PRIMARY KEY (id),
    CONSTRAINT ck_exportacao_tipo CHECK (tipo IN ('ACESSOS', 'GOVERNANCA')),
    CONSTRAINT ck_exportacao_situacao CHECK (situacao IN ('PROCESSANDO', 'CONCLUIDA', 'FALHOU'))
);
CREATE INDEX ix_exportacao_solicitante ON exportacao (solicitante_id, criado_em);
