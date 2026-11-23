package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.EventoOutbox;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, UUID> {

    List<EventoOutbox> findTop100ByPublicadoEmIsNullOrderByOcorridoEmAsc();

    long countByPublicadoEmIsNull();
}
