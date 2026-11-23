package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.ContratoFiscal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratoFiscalRepository extends JpaRepository<ContratoFiscal, UUID> {

    List<ContratoFiscal> findByContratoIdOrderByInicioDesc(UUID contratoId);
}
