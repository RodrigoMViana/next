package br.com.subti.next.configuracao.application;

import br.com.subti.next.configuracao.domain.model.ChaveIdempotencia;
import br.com.subti.next.configuracao.domain.model.UsuarioAtual;
import br.com.subti.next.configuracao.domain.repositorio.ChaveIdempotenciaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key em criações críticas (Arquitetura 6.4, RNF06): repetir a mesma requisição com a mesma
 * chave não cria um segundo registro; devolve o recurso criado na primeira vez.
 */
@Service
public class IdempotenciaService {

    /** Resultado: o recurso e se foi uma repetição. */
    public record Resultado<T>(T corpo, boolean repetido) {
    }

    private final ChaveIdempotenciaRepository chaves;

    public IdempotenciaService(ChaveIdempotenciaRepository chaves) {
        this.chaves = chaves;
    }

    @Transactional
    public <T> Resultado<T> executar(String chave, String rota, Supplier<T> criar, Function<T, UUID> idDe,
                                     Function<UUID, T> obter) {
        if (chave == null || chave.isBlank()) {
            return new Resultado<>(criar.get(), false);
        }
        String usuario = UsuarioAtual.get().id();
        String k = chave.length() > 100 ? chave.substring(0, 100) : chave;
        Optional<ChaveIdempotencia> existente = chaves.findById(new ChaveIdempotencia.Pk(k, usuario));
        if (existente.isPresent()) {
            if (!existente.get().getRota().equals(rota)) {
                throw Suporte.erroCampo("Idempotency-Key", "A chave de idempotência já foi usada em outra operação.");
            }
            return new Resultado<>(obter.apply(UUID.fromString(existente.get().getRecursoId())), true);
        }
        T criado = criar.get();
        chaves.save(new ChaveIdempotencia(k, usuario, rota, idDe.apply(criado).toString()));
        return new Resultado<>(criado, false);
    }
}
