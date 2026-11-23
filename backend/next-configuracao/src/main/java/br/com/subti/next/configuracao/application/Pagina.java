package br.com.subti.next.configuracao.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Página de resultados (Arquitetura 6.4: page, size, sort e totalElements). */
public record Pagina<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int TAMANHO_MAXIMO = 100;

    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> conversor) {
        return new Pagina<>(page.getContent().stream().map(conversor).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Cria o Pageable a partir de parâmetros da requisição. A ordenação aceita só campos da lista
     * permitida (evita ordenar por atributos arbitrários). Formato: "campo,asc" ou "campo,desc".
     */
    public static Pageable pageable(Integer page, Integer size, String sort, Map<String, String> permitidos,
                                    String padrao) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, TAMANHO_MAXIMO);
        String campo = padrao;
        Sort.Direction direcao = Sort.Direction.ASC;
        if (sort != null && !sort.isBlank()) {
            String[] partes = sort.split(",");
            if (permitidos.containsKey(partes[0])) {
                campo = permitidos.get(partes[0]);
            }
            if (partes.length > 1 && partes[1].equalsIgnoreCase("desc")) {
                direcao = Sort.Direction.DESC;
            }
        }
        return PageRequest.of(p, s, Sort.by(direcao, campo));
    }
}
