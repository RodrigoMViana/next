package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.ContratoAditivo;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratoAditivoRepository extends JpaRepository<ContratoAditivo, UUID> {

    List<ContratoAditivo> findByContratoIdOrderByDataAssinaturaAscCreatedAtAsc(UUID contratoId);

    boolean existsByContratoIdAndNumeroIgnoreCase(UUID contratoId, String numero);
}
