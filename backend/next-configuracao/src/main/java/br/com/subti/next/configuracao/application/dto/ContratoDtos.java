package br.com.subti.next.configuracao.application.dto;

import br.com.subti.next.configuracao.domain.model.Contrato;
import br.com.subti.next.configuracao.domain.model.ContratoAditivo;
import br.com.subti.next.configuracao.domain.model.ContratoFiscal;
import br.com.subti.next.configuracao.domain.model.ContratoItem;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.regra.Cnpj;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** DTOs do contrato (N01). */
public final class ContratoDtos {

    private ContratoDtos() {
    }

    public record ItemEntrada(@NotNull UUID servicoId, @NotNull @Positive BigDecimal volume,
                              @NotNull @PositiveOrZero BigDecimal valorUnitario) {
    }

    public record FiscalEntrada(@NotBlank String usuarioId, @NotNull ContratoFiscal.TipoFiscal tipoFiscal,
                                @NotNull LocalDate inicio, LocalDate fim) {
    }

    /** Cadastro e alteração. Os fiscais iniciais só são informados na criação (RN06). */
    public record ContratoEntrada(
            @NotBlank @Size(max = 30) String numero,
            @NotBlank @Size(max = 1000) String objeto,
            @NotNull UUID contratadaId,
            @NotNull UUID calendarioId,
            @NotNull LocalDate vigenciaInicio,
            @NotNull LocalDate vigenciaFim,
            @NotEmpty @Valid List<ItemEntrada> itens,
            @NotNull List<UUID> fonteIds,
            @NotNull List<UUID> sistemaIds,
            @Valid List<FiscalEntrada> fiscais) {
    }

    public record AditivoEntrada(
            @NotBlank @Size(max = 30) String numero,
            boolean aditivoPrazo,
            boolean aditivoValor,
            LocalDate novaVigenciaFim,
            BigDecimal valorAcrescimo,
            @NotNull LocalDate dataAssinatura,
            @NotBlank @Size(max = 2000) String justificativa) {
    }

    public record SubstituicaoFiscal(@NotBlank String usuarioId, @NotNull LocalDate inicio,
                                     @NotBlank @Size(max = 500) String motivo) {
    }

    public record EncerramentoFiscal(@NotNull LocalDate fim, @NotBlank @Size(max = 500) String motivo) {
    }

    public record Ref(UUID id, String codigo, String nome) {
    }

    public record ItemSaida(UUID id, Ref servico, Ref unidadeMedida, BigDecimal volume, BigDecimal valorUnitario,
                            BigDecimal valorTotal) {
        static ItemSaida de(ContratoItem i) {
            return new ItemSaida(i.getId(),
                    new Ref(i.getServico().getId(), i.getServico().getCodigo(), i.getServico().getNome()),
                    new Ref(i.getUnidadeMedida().getId(), i.getUnidadeMedida().getSigla(), i.getUnidadeMedida().getNome()),
                    i.getVolume(), i.getValorUnitario(), i.getValorTotal());
        }
    }

    public record AditivoSaida(UUID id, String numero, boolean aditivoPrazo, boolean aditivoValor,
                               LocalDate novaVigenciaFim, BigDecimal valorAcrescimo, LocalDate dataAssinatura,
                               String justificativa, Instant createdAt, String createdByNome) {
        public static AditivoSaida de(ContratoAditivo a) {
            return new AditivoSaida(a.getId(), a.getNumero(), a.isAditivoPrazo(), a.isAditivoValor(),
                    a.getNovaVigenciaFim(), a.getValorAcrescimo(), a.getDataAssinatura(), a.getJustificativa(),
                    a.getCreatedAt(), a.getCreatedByNome());
        }
    }

    public record FiscalSaida(UUID id, String usuarioId, String nomeExibicao, ContratoFiscal.TipoFiscal tipoFiscal,
                              LocalDate inicio, LocalDate fim, boolean vigente, String motivoEncerramento,
                              UUID substituidoPorId) {
        public static FiscalSaida de(ContratoFiscal f, LocalDate hoje) {
            return new FiscalSaida(f.getId(), f.getUsuarioId(), f.getNomeExibicao(), f.getTipoFiscal(), f.getInicio(),
                    f.getFim(), f.vigenteEm(hoje), f.getMotivoEncerramento(), f.getSubstituidoPorId());
        }
    }

    public record SistemaSaida(UUID sistemaId, String sistemaNome) {
    }

    /** Resumo para listas paginadas (RF01). */
    public record ContratoResumo(UUID id, String numero, String objeto, Ref contratada,
                                 LocalDate vigenciaInicio, LocalDate vigenciaFim, LocalDate vigenciaFimEfetiva,
                                 BigDecimal valorEfetivo, boolean vigente, Situacao situacao) {
        public static ContratoResumo de(Contrato c, LocalDate hoje) {
            return new ContratoResumo(c.getId(), c.getNumero(), c.getObjeto(),
                    new Ref(c.getContratada().getId(), Cnpj.formatar(c.getContratada().getCnpj()),
                            c.getContratada().getRazaoSocial()),
                    c.getVigenciaInicio(), c.getVigenciaFim(), c.vigenciaFimEfetiva(), c.valorEfetivo(),
                    c.utilizavelEm(hoje), c.getSituacao());
        }
    }

    /** Detalhe com valores originais e efetivos (RF03). */
    public record ContratoDetalhe(UUID id, String numero, String objeto, Ref contratada, Ref calendario,
                                  LocalDate vigenciaInicio, LocalDate vigenciaFim, LocalDate vigenciaFimEfetiva,
                                  BigDecimal valorOriginal, BigDecimal valorEfetivo, boolean vigente,
                                  boolean temFiscalVigente, List<ItemSaida> itens, List<Ref> fontes,
                                  List<SistemaSaida> sistemas, List<AditivoSaida> aditivos,
                                  List<FiscalSaida> fiscais, Situacao situacao, String justificativaSituacao,
                                  long version, Instant createdAt, Instant updatedAt) {
        public static ContratoDetalhe de(Contrato c, LocalDate hoje) {
            return new ContratoDetalhe(c.getId(), c.getNumero(), c.getObjeto(),
                    new Ref(c.getContratada().getId(), Cnpj.formatar(c.getContratada().getCnpj()),
                            c.getContratada().getRazaoSocial()),
                    new Ref(c.getCalendario().getId(), c.getCalendario().getFusoHorario(), c.getCalendario().getNome()),
                    c.getVigenciaInicio(), c.getVigenciaFim(), c.vigenciaFimEfetiva(), c.valorOriginal(),
                    c.valorEfetivo(), c.utilizavelEm(hoje), !c.fiscaisVigentes(hoje).isEmpty(),
                    c.getItens().stream().map(ItemSaida::de).toList(),
                    c.getFontes().stream().map(f -> new Ref(f.getId(), f.getCodigo(), f.getNome()))
                            .sorted(Comparator.comparing(Ref::codigo)).toList(),
                    c.getSistemas().stream().map(s -> new SistemaSaida(s.getSistemaId(), s.getSistemaNome())).toList(),
                    c.getAditivos().stream().sorted(Comparator.comparing(ContratoAditivo::getDataAssinatura))
                            .map(AditivoSaida::de).toList(),
                    c.getFiscais().stream().sorted(Comparator.comparing(ContratoFiscal::getInicio).reversed())
                            .map(f -> FiscalSaida.de(f, hoje)).toList(),
                    c.getSituacao(), c.getJustificativaSituacao(), c.versao(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
