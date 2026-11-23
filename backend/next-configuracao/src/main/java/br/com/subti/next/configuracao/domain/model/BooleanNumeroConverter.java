package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Grava booleanos como NUMBER(1) (0/1), compatível com Oracle 19c, que não tem tipo BOOLEAN em tabela. */
@Converter(autoApply = true)
public class BooleanNumeroConverter implements AttributeConverter<Boolean, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Boolean valor) {
        return valor == null ? null : (valor ? 1 : 0);
    }

    @Override
    public Boolean convertToEntityAttribute(Integer valor) {
        return valor == null ? null : valor != 0;
    }
}
