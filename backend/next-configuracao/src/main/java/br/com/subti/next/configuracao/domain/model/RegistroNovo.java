package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Base de registros com UUID atribuído na aplicação e sem coluna de versão (registros somente inclusão).
 * Informa ao Spring Data que a instância é nova, para que a gravação use INSERT direto (sem merge).
 */
@MappedSuperclass
public abstract class RegistroNovo implements Persistable<UUID> {

    @Transient
    private boolean novo = true;

    @Override
    public boolean isNew() {
        return novo;
    }

    @PostLoad
    @PostPersist
    void marcarPersistido() {
        novo = false;
    }
}
