import { describe, expect, it } from 'vitest'
import { calcularDigitosCnpj, formatarCnpj, mascararCnpj, normalizarCnpj, validarCnpj } from '@/lib/cnpj'

describe('CNPJ numérico e alfanumérico (RN25 a RN28, RNF13)', () => {
  it.each(['11.222.333/0001-81', '11222333000181', '12.ABC.345/01DE-35', '12abc34501de35', ' 12.abc.345/01de-35 ', 'A1B2C3D4E5F668'])(
    'aceita %s',
    (v) => expect(validarCnpj(v)).toBeNull(),
  )

  it.each([
    ['11.222.333/0001-82', 'Dígitos verificadores do CNPJ não conferem.'],
    ['12.ABC.345/01DE-3A', 'Os dois dígitos verificadores do CNPJ devem ser numéricos.'],
    ['12#BC34501DE35', 'O CNPJ aceita apenas letras de A a Z e números de 0 a 9, sem acentos ou símbolos.'],
    ['1122233300018', 'O CNPJ deve ter 14 posições (sem contar a máscara).'],
    ['00000000000000', 'CNPJ inválido: todos os caracteres são iguais.'],
    ['', 'Informe o CNPJ.'],
  ])('rejeita %s', (v, msg) => expect(validarCnpj(v)).toBe(msg))

  it('calcula o DV com valor ASCII − 48 e módulo 11 (RN27)', () => {
    expect(calcularDigitosCnpj('12ABC34501DE')).toBe('35')
    expect(calcularDigitosCnpj('112223330001')).toBe('81')
    expect(() => calcularDigitosCnpj('123')).toThrow('A base do CNPJ deve ter 12 posições.')
  })

  it('normaliza, formata e aplica máscara progressiva', () => {
    expect(normalizarCnpj('12.abc.345/01de-35')).toBe('12ABC34501DE35')
    expect(formatarCnpj('12ABC34501DE35')).toBe('12.ABC.345/01DE-35')
    expect(mascararCnpj('12abc3')).toBe('12.ABC.3')
    expect(mascararCnpj('12ABC34501DE35999')).toBe('12.ABC.345/01DE-35')
  })
})
