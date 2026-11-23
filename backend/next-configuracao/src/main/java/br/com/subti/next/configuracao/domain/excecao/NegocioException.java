package br.com.subti.next.configuracao.domain.excecao;

import java.util.List;

/**
 * Violação de regra de negócio. Convertida em Problem Details (RFC 9457) com status 422
 * pelo handler único do módulo.
 */
public class NegocioException extends RuntimeException {

    private final String codigo;
    private final String titulo;
    private final transient List<ErroCampo> erros;

    public NegocioException(String codigo, String titulo, String detalhe) {
        this(codigo, titulo, detalhe, List.of());
    }

    public NegocioException(String codigo, String titulo, String detalhe, List<ErroCampo> erros) {
        super(detalhe);
        this.codigo = codigo;
        this.titulo = titulo;
        this.erros = List.copyOf(erros);
    }

    public static NegocioException campo(String codigo, String titulo, String campo, String mensagem) {
        return new NegocioException(codigo, titulo, mensagem, List.of(new ErroCampo(campo, mensagem)));
    }

    public String getCodigo() {
        return codigo;
    }

    public String getTitulo() {
        return titulo;
    }

    public List<ErroCampo> getErros() {
        return erros;
    }

    public int status() {
        return 422;
    }
}
