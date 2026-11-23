package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.EventoProcessado;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcessadoRepository extends JpaRepository<EventoProcessado, UUID> {
}
