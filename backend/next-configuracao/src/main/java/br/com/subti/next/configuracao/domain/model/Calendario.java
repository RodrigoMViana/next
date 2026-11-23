package br.com.subti.next.configuracao.domain.model;

import br.com.subti.next.configuracao.domain.regra.CalculadoraExpediente;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

/** N10 - Calendário: fuso horário, expediente por dia da semana e feriados (RN17, RN18). */
@Getter
@Setter
@Entity
@Table(name = "calendario")
public class Calendario extends CadastroComSituacao {

    private String nome;
    private String descricao;

    @Column(name = "fuso_horario")
    private String fusoHorario;

    /** RN18: a alteração vale a partir desta data e não recalcula chamados em andamento. */
    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "calendario_id", nullable = false)
    @OrderBy("diaSemana, horaInicio")
    private List<CalendarioExpediente> expedientes = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "calendario_id", nullable = false)
    @OrderBy("dataFeriado")
    private List<CalendarioFeriado> feriados = new ArrayList<>();

    /** Cria a calculadora de expediente a partir do cadastro. */
    public CalculadoraExpediente calculadora() {
        Map<DayOfWeek, List<CalculadoraExpediente.Intervalo>> mapa = new EnumMap<>(DayOfWeek.class);
        for (CalendarioExpediente e : expedientes) {
            mapa.computeIfAbsent(DayOfWeek.of(e.getDiaSemana()), d -> new ArrayList<>())
                    .add(new CalculadoraExpediente.Intervalo(e.getHoraInicio(), e.getHoraFim()));
        }
        List<CalculadoraExpediente.Feriado> lista = feriados.stream()
                .map(f -> new CalculadoraExpediente.Feriado(f.getDataFeriado(), f.isRecorrenteAnual())).toList();
        return new CalculadoraExpediente(ZoneId.of(fusoHorario), mapa, lista);
    }

    @Override
    public String tipoEntidade() {
        return "calendario";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nome", nome);
        m.put("descricao", descricao);
        m.put("fusoHorario", fusoHorario);
        m.put("vigenciaInicio", vigenciaInicio == null ? null : vigenciaInicio.toString());
        m.put("expediente", expedientes.stream()
                .map(e -> e.getDiaSemana() + " " + e.getHoraInicio() + "-" + e.getHoraFim())
                .collect(Collectors.joining("; ")));
        m.put("feriados", feriados.stream()
                .map(f -> f.getDataFeriado() + " " + f.getDescricao() + (f.isRecorrenteAnual() ? " (anual)" : ""))
                .collect(Collectors.joining("; ")));
        m.put("situacao", getSituacao().name());
        return m;
    }
}
