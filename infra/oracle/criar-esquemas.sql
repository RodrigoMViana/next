-- =============================================================================
-- Next - Preparação do Oracle (19c ou superior) para HOMOLOGAÇÃO/PRODUÇÃO
-- Executar como DBA no PDB de destino. Ajuste tablespace, senhas e cotas.
--
-- Modelo de 3 usuários por esquema (Arquitetura 7.1 e 7.3):
--   *_OWNER  dono das tabelas; usado SOMENTE pelo Flyway na implantação
--   *_APP    usuário da aplicação: SELECT/INSERT/UPDATE nas tabelas de negócio e
--            SOMENTE SELECT/INSERT nas tabelas de registro imutável (append-only)
-- =============================================================================

-- ---------- Módulo Configuração
CREATE USER NEXT_CONFIGURACAO IDENTIFIED BY "&senha_owner_configuracao" QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW TO NEXT_CONFIGURACAO;

CREATE USER NEXT_CONFIGURACAO_APP IDENTIFIED BY "&senha_app_configuracao";
GRANT CREATE SESSION TO NEXT_CONFIGURACAO_APP;

-- ---------- BFF (trilha de acesso LGPD e exportações)
CREATE USER NEXT_AUDIT IDENTIFIED BY "&senha_owner_audit" QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW TO NEXT_AUDIT;

CREATE USER NEXT_AUDIT_APP IDENTIFIED BY "&senha_app_audit";
GRANT CREATE SESSION TO NEXT_AUDIT_APP;

-- Depois da primeira migração Flyway (feita com os usuários *_OWNER), execute conceder-permissoes.sql.
