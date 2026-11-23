package br.com.subti.next.configuracao.application.dto;

import br.com.subti.next.configuracao.domain.model.Contratada;
import br.com.subti.next.configuracao.domain.model.ContratadaAlteracao;
import br.com.subti.next.configuracao.domain.model.ContratadaMembro;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.regra.Cnpj;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** DTOs da contratada (N02). */
public final class ContratadaDtos {

    private ContratadaDtos() {
    }

    /** O CNPJ pode vir com ou sem máscara, em minúsculas ou maiúsculas (RN28). */
    public record ContratadaEntrada(
            @NotBlank String cnpj,
            @NotBlank @Size(max = 200) String razaoSocial,
            @Size(max = 200) String nomeFantasia) {
    }

    public record ContratadaSaida(UUID id, String cnpj, String cnpjFormatado, boolean cnpjAlfanumerico,
                                  String razaoSocial, String nomeFantasia, long contratosAtivos,
                                  Situacao situacao, String justificativaSituacao, long version,
                                  Instant createdAt, Instant updatedAt) {
        public static ContratadaSaida de(Contratada c, long contratosAtivos) {
            return new ContratadaSaida(c.getId(), c.getCnpj(), Cnpj.formatar(c.getCnpj()),
                    !c.getCnpj().chars().allMatch(Character::isDigit), c.getRazaoSocial(), c.getNomeFantasia(),
                    contratosAtivos, c.getSituacao(), c.getJustificativaSituacao(), c.versao(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record ValidacaoCnpj(String entrada, String normalizado, String formatado, boolean valido,
                                boolean alfanumerico, String mensagem) {
    }

    public record MembroEntrada(@NotBlank String usuarioId, @NotNull ContratadaMembro.Papel papel,
                                @NotNull LocalDate inicio, LocalDate fim) {
    }

    public record EncerramentoMembro(@NotNull LocalDate fim) {
    }

    public record MembroSaida(UUID id, String usuarioId, String nomeExibicao, ContratadaMembro.Papel papel,
                              LocalDate inicio, LocalDate fim, boolean vigente, boolean mfaObrigatorio,
                              long version) {
        public static MembroSaida de(ContratadaMembro m, LocalDate hoje) {
            return new MembroSaida(m.getId(), m.getUsuarioId(), m.getNomeExibicao(), m.getPapel(), m.getInicio(),
                    m.getFim(), m.vigenteEm(hoje), true, m.versao());
        }
    }

    public record AlteracaoEntrada(@NotNull LocalDate dataAlteracao, @NotBlank @Size(max = 40) String tipo,
                                   @NotBlank @Size(max = 2000) String descricao) {
    }

    public record AlteracaoSaida(UUID id, LocalDate dataAlteracao, String tipo, String descricao,
                                 String anexoNome, String anexoTipo, Long anexoTamanho, Instant createdAt,
                                 String createdByNome) {
        public static AlteracaoSaida de(ContratadaAlteracao a) {
            return new AlteracaoSaida(a.getId(), a.getDataAlteracao(), a.getTipo(), a.getDescricao(),
                    a.getAnexoNome(), a.getAnexoTipo(), a.getAnexoTamanho(), a.getCreatedAt(), a.getCreatedByNome());
        }
    }

    public record Anexo(String nome, String tipo, byte[] conteudo) {
    }
}
