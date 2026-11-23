package br.com.subti.next.bff.web;

/** Erro do próprio BFF, convertido em Problem Details. */
public class ErroBff extends RuntimeException {

    private final int status;
    private final String codigo;
    private final String titulo;
    private final String campo;

    public ErroBff(int status, String codigo, String titulo, String detalhe) {
        this(status, codigo, titulo, detalhe, null);
    }

    public ErroBff(int status, String codigo, String titulo, String detalhe, String campo) {
        super(detalhe);
        this.status = status;
        this.codigo = codigo;
        this.titulo = titulo;
        this.campo = campo;
    }

    public int getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getCampo() {
        return campo;
    }
}
