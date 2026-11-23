package br.com.subti.next.configuracao.domain.regra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** RN25 a RN28 e RNF13: CNPJ numérico, alfanumérico, com e sem máscara e DV inválido. */
class CnpjTest {

    @ParameterizedTest(name = "válido: {0}")
    @ValueSource(strings = {"11.222.333/0001-81", "11222333000181", "12.ABC.345/01DE-35", "12ABC34501DE35",
            "12abc34501de35", " 12.abc.345/01de-35 ", "A1B2C3D4E5F668"})
    @DisplayName("aceita CNPJ numérico e alfanumérico, com ou sem máscara, convertendo minúsculas")
    void aceitaValidos(String entrada) {
        assertThat(Cnpj.validar(entrada)).isEmpty();
        assertThat(Cnpj.valido(entrada)).isTrue();
    }

    @ParameterizedTest(name = "inválido: {0} -> {1}")
    @CsvSource(delimiter = ';', value = {
            "11.222.333/0001-82; Dígitos verificadores do CNPJ não conferem.",
            "12.ABC.345/01DE-36; Dígitos verificadores do CNPJ não conferem.",
            "12.ABC.345/01DE-3A; Os dois dígitos verificadores do CNPJ devem ser numéricos.",
            "12.ÁBC.345/01DE-35; O CNPJ aceita apenas letras de A a Z e números de 0 a 9, sem acentos ou símbolos.",
            "12*ABC*345*01DE-35; O CNPJ deve ter 14 posições (sem contar a máscara).",
            "12#BC34501DE35; O CNPJ aceita apenas letras de A a Z e números de 0 a 9, sem acentos ou símbolos.",
            "1122233300018; O CNPJ deve ter 14 posições (sem contar a máscara).",
            "00000000000000; CNPJ inválido: todos os caracteres são iguais."})
    void rejeitaInvalidos(String entrada, String mensagem) {
        assertThat(Cnpj.validar(entrada)).contains(mensagem);
        assertThatThrownBy(() -> Cnpj.of(entrada)).isInstanceOf(IllegalArgumentException.class).hasMessage(mensagem);
    }

    @Test
    void rejeitaVazio() {
        assertThat(Cnpj.validar(null)).contains("Informe o CNPJ.");
        assertThat(Cnpj.validar("  ")).contains("Informe o CNPJ.");
    }

    @Test
    @DisplayName("RN27: valor do caractere é ASCII - 48 e DV por módulo 11 com pesos 2 a 9")
    void calculaDigitos() {
        assertThat(Cnpj.calcularDigitos("12ABC34501DE")).isEqualTo("35");
        assertThat(Cnpj.calcularDigitos("112223330001")).isEqualTo("81");
        assertThatThrownBy(() -> Cnpj.calcularDigitos("123")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RN28: armazena sem máscara e em maiúsculas; exibe com máscara")
    void normalizaEFormata() {
        Cnpj c = Cnpj.of("12.abc.345/01de-35");
        assertThat(c.valor()).isEqualTo("12ABC34501DE35");
        assertThat(c.formatado()).isEqualTo("12.ABC.345/01DE-35");
        assertThat(c.toString()).isEqualTo("12.ABC.345/01DE-35");
        assertThat(c.alfanumerico()).isTrue();
        assertThat(Cnpj.of("11222333000181").alfanumerico()).isFalse();
        assertThat(Cnpj.formatar("123")).isEqualTo("123");
        assertThat(Cnpj.normalizar(null)).isEmpty();
    }

    @Test
    @DisplayName("RN26: numérico e alfanumérico convivem; igualdade sobre o valor sem máscara")
    void igualdade() {
        assertThat(Cnpj.of("11.222.333/0001-81")).isEqualTo(Cnpj.of("11222333000181"))
                .hasSameHashCodeAs(Cnpj.of("11222333000181"));
        assertThat(Cnpj.of("11.222.333/0001-81")).isNotEqualTo(Cnpj.of("12ABC34501DE35"));
        assertThat("12ABC34501DE35").matches(Cnpj.PADRAO);
    }
}
