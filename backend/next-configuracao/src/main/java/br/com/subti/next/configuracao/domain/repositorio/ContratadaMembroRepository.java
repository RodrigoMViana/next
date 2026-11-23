package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.ContratadaMembro;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratadaMembroRepository extends JpaRepository<ContratadaMembro, UUID> {

    List<ContratadaMembro> findByContratadaIdOrderByInicioDesc(UUID contratadaId);

    List<ContratadaMembro> findByUsuarioId(String usuarioId);
}
