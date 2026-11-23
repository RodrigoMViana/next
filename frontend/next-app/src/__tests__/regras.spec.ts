import { describe, expect, it } from 'vitest'
import { avaliar, Calculadora, prazo, validarRegra } from '@/api/demo/regras'

// Mesmo cenário dos testes Java: seg-sex 8-12 e 14-18; 20/11 recorrente e 24/11/2026 feriados.
const exp = [1, 2, 3, 4, 5].flatMap((d) => [{ diaSemana: d, horaInicio: '08:00', horaFim: '12:00' }, { diaSemana: d, horaInicio: '14:00', horaFim: '18:00' }])
const cal = new Calculadora('America/Sao_Paulo', exp, [
  { data: '2025-11-20', descricao: 'Consciência Negra', abrangencia: 'NACIONAL', recorrenteAnual: true },
  { data: '2026-11-24', descricao: 'Feriado local', abrangencia: 'MUNICIPAL', recorrenteAnual: false },
])
// São Paulo = UTC-3 em novembro de 2026 (sem horário de verão)
const em = (dia: number, h: number, m = 0) => Date.UTC(2026, 10, dia, h + 3, m)
const H = 3_600_000

describe('Calendário (RN12, RN17) — porte do Java para o modo demonstração', () => {
  it('conta só o expediente', () => {
    expect(cal.tempoUtil(em(23, 10), em(23, 15))).toBe(3 * H)
    expect(cal.tempoUtil(em(27, 17), em(30, 9))).toBe(2 * H)
    expect(cal.tempoUtil(em(23, 17), em(25, 9))).toBe(2 * H)
  })
  it('soma tempo útil atravessando almoço, noite e feriado', () => {
    expect(cal.somarTempoUtil(em(23, 11), 2 * H)).toBe(em(23, 15))
    expect(cal.somarTempoUtil(em(23, 17), 4 * H)).toBe(em(25, 11))
    expect(cal.somarTempoUtil(em(28, 10), H / 2)).toBe(em(30, 8, 30))
  })
  it('soma dias úteis', () => {
    expect(cal.somarDiasUteis(em(23, 10), 1)).toBe(em(23, 18))
    expect(cal.somarDiasUteis(em(23, 10), 3)).toBe(em(26, 18))
    expect(cal.somarDiasUteis(em(23, 19), 1)).toBe(em(25, 18))
  })
})

describe('SLA (RN11 a RN13, RN22)', () => {
  it('valida combinações', () => {
    expect(() => validarRegra('HORARIO_COMERCIAL', 'HORAS', 1, false)).toThrow(/exige calendário/)
    expect(() => validarRegra('CORRIDO_24X7', 'DIAS_UTEIS', 1, false)).toThrow(/dias corridos/)
    expect(() => validarRegra('CORRIDO_24X7', 'PERCENTUAL', 101, false)).toThrow(/entre 0 e 100/)
    expect(() => validarRegra('CORRIDO_24X7', 'HORAS', 0, false)).toThrow(/maior que zero/)
  })
  it('calcula prazos', () => {
    expect(prazo(em(23, 10), 'CORRIDO_24X7', 'MINUTOS', 30, cal)).toBe(em(23, 10, 30))
    expect(prazo(em(23, 10), 'HORARIO_COMERCIAL', 'HORAS', 4, cal)).toBe(em(23, 16))
    expect(prazo(em(23, 10), 'HORARIO_COMERCIAL', 'DIAS_UTEIS', 2, cal)).toBe(em(25, 18))
  })
  it('avalia situação com pausas e limiar', () => {
    expect(avaliar(em(23, 8), em(23, 9), [], 'HORARIO_COMERCIAL', 'HORAS', 4, cal, 80).situacao).toBe('EM_DIA')
    expect(avaliar(em(23, 8), em(23, 11, 30), [], 'HORARIO_COMERCIAL', 'HORAS', 4, cal, 80).situacao).toBe('EM_RISCO')
    expect(avaliar(em(23, 8), em(23, 15), [], 'HORARIO_COMERCIAL', 'HORAS', 4, cal, 80).situacao).toBe('VIOLADO')
    const pausado = avaliar(em(23, 8), em(23, 11), [{ inicio: em(23, 9), fim: em(23, 10), motivo: 'Aguardando usuário' }], 'HORARIO_COMERCIAL', 'HORAS', 4, cal, 80)
    expect(pausado.consumido).toBe(2 * H)
    expect(pausado.prazo).toBe(em(23, 15))
    expect(() => avaliar(em(23, 8), em(23, 9), [{ inicio: em(23, 8), fim: null, motivo: ' ' }], 'CORRIDO_24X7', 'HORAS', 4, cal, 80)).toThrow(/motivo/)
  })
})
