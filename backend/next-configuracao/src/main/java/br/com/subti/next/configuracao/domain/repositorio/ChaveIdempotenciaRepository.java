package br.com.subti.next.configuracao.domain.repositorio;

import br.com.subti.next.configuracao.domain.model.ChaveIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChaveIdempotenciaRepository extends JpaRepository<ChaveIdempotencia, ChaveIdempotencia.Pk> {
}
