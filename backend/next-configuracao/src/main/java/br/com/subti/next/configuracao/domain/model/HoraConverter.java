package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;

/** Hora como texto HH:mm (Oracle não tem tipo TIME). */
@Converter
public class HoraConverter implements AttributeConverter<LocalTime, String> {

    @Override
    public String convertToDatabaseColumn(LocalTime hora) {
        return hora == null ? null : String.format("%02d:%02d", hora.getHour(), hora.getMinute());
    }

    @Override
    public LocalTime convertToEntityAttribute(String texto) {
        return texto == null ? null : LocalTime.parse(texto);
    }
}
