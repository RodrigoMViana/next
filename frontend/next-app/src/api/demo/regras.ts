/**
 * Porte em TypeScript das regras de domínio do módulo (CalculadoraExpediente e CalculoSla), usado
 * apenas pelo modo demonstração (GitHub Pages). A fonte da verdade é o Java (next-configuracao).
 */
import type { Expediente, Feriado, FormaContagem, UnidadeMeta } from '../tipos'

const MINUTO = 60_000
const MAX_DIAS = 3660

interface Partes {
  ano: number
  mes: number
  dia: number
  hora: number
  minuto: number
  /** 1 = segunda ... 7 = domingo (ISO). */
  diaSemana: number
}

const formatadores = new Map<string, Intl.DateTimeFormat>()
function formatador(fuso: string) {
  let f = formatadores.get(fuso)
  if (!f) {
    f = new Intl.DateTimeFormat('en-US', {
      timeZone: fuso, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
      second: '2-digit', hourCycle: 'h23', weekday: 'short',
    })
    formatadores.set(fuso, f)
  }
  return f
}

export function partes(instante: number, fuso: string): Partes {
  const p: Record<string, string> = {}
  for (const x of formatador(fuso).formatToParts(new Date(instante))) p[x.type] = x.value
  const dias: Record<string, number> = { Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6, Sun: 7 }
  return { ano: +p.year!, mes: +p.month!, dia: +p.day!, hora: +p.hour! % 24, minuto: +p.minute!, diaSemana: dias[p.weekday!]! }
}

/** Instante correspondente a um horário de parede no fuso. */
export function instante(ano: number, mes: number, dia: number, hora: number, minuto: number, fuso: string): number {
  const alvo = Date.UTC(ano, mes - 1, dia, hora, minuto)
  let palpite = alvo
  for (let i = 0; i < 3; i++) {
    const p = partes(palpite, fuso)
    const parede = Date.UTC(p.ano, p.mes - 1, p.dia, p.hora, p.minuto)
    palpite += alvo - parede
  }
  return palpite
}

const minutosDe = (hhmm: string) => {
  const [h, m] = hhmm.split(':').map(Number)
  return h! * 60 + m!
}

export class Calculadora {
  private readonly porDia = new Map<number, Array<[number, number]>>()
  private readonly fixos = new Set<string>()
  private readonly recorrentes = new Set<string>()

  constructor(readonly fuso: string, expedientes: Expediente[], feriados: Feriado[]) {
    for (const e of expedientes) {
      const lista = this.porDia.get(e.diaSemana) ?? []
      lista.push([minutosDe(e.horaInicio), minutosDe(e.horaFim)])
      this.porDia.set(e.diaSemana, lista)
    }
    for (const l of this.porDia.values()) l.sort((a, b) => a[0] - b[0])
    for (const f of feriados) (f.recorrenteAnual ? this.recorrentes : this.fixos).add(f.recorrenteAnual ? f.data.slice(5) : f.data)
  }

  private chave(a: number, m: number, d: number) {
    return `${a}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
  }

  feriado(a: number, m: number, d: number) {
    const k = this.chave(a, m, d)
    return this.fixos.has(k) || this.recorrentes.has(k.slice(5))
  }

  private intervalos(a: number, m: number, d: number, dow: number) {
    return this.feriado(a, m, d) ? [] : (this.porDia.get(dow) ?? [])
  }

  /** Itera dias a partir da data de parede do instante. */
  private *dias(desde: number) {
    const p = partes(desde, this.fuso)
    const base = Date.UTC(p.ano, p.mes - 1, p.dia)
    for (let i = 0; i < MAX_DIAS; i++) {
      const d = new Date(base + i * 86_400_000)
      const a = d.getUTCFullYear(), m = d.getUTCMonth() + 1, dia = d.getUTCDate()
      const dow = ((d.getUTCDay() + 6) % 7) + 1
      yield { a, m, dia, lista: this.intervalos(a, m, dia, dow) }
    }
  }

  tempoUtil(inicio: number, fim: number): number {
    if (fim <= inicio) return 0
    let total = 0
    for (const { a, m, dia, lista } of this.dias(inicio)) {
      if (instante(a, m, dia, 0, 0, this.fuso) > fim) break
      for (const [ini, f] of lista) {
        const de = Math.max(instante(a, m, dia, Math.floor(ini / 60), ini % 60, this.fuso), inicio)
        const ate = Math.min(instante(a, m, dia, Math.floor(f / 60), f % 60, this.fuso), fim)
        if (ate > de) total += ate - de
      }
    }
    return total
  }

  somarTempoUtil(inicio: number, duracao: number): number {
    let restante = duracao
    for (const { a, m, dia, lista } of this.dias(inicio)) {
      for (const [ini, f] of lista) {
        const ia = instante(a, m, dia, Math.floor(ini / 60), ini % 60, this.fuso)
        const fb = instante(a, m, dia, Math.floor(f / 60), f % 60, this.fuso)
        if (fb <= inicio) continue
        const de = Math.max(ia, inicio)
        if (restante === 0) return de
        if (fb - de >= restante) return de + restante
        restante -= fb - de
      }
    }
    throw new Error('O calendário não tem expediente suficiente para calcular o prazo.')
  }

  somarDiasUteis(inicio: number, dias: number): number {
    let contados = 0
    for (const { a, m, dia, lista } of this.dias(inicio)) {
      if (!lista.length) continue
      const ultimo = lista[lista.length - 1]![1]
      const fimDia = instante(a, m, dia, Math.floor(ultimo / 60), ultimo % 60, this.fuso)
      if (fimDia > inicio && ++contados === dias) return fimDia
    }
    throw new Error('O calendário não tem dias úteis suficientes para calcular o prazo.')
  }
}

export function validarRegra(forma: FormaContagem, unidade: UnidadeMeta, meta: number, temCalendario: boolean) {
  if (!(meta > 0)) throw new Error('A meta deve ser maior que zero.')
  if (unidade === 'PERCENTUAL') {
    if (meta > 100) throw new Error('Meta percentual deve estar entre 0 e 100.')
    return
  }
  if (forma === 'HORARIO_COMERCIAL' && !temCalendario) throw new Error('Regra com contagem em horário comercial exige calendário.')
  if (forma === 'HORARIO_COMERCIAL' && unidade === 'DIAS_CORRIDOS') throw new Error('Em horário comercial use dias úteis, horas ou minutos.')
  if (forma === 'CORRIDO_24X7' && unidade === 'DIAS_UTEIS') throw new Error('Em contagem corrida (24x7) use dias corridos, horas ou minutos.')
}

export function duracaoMeta(unidade: UnidadeMeta, meta: number): number {
  const fator = unidade === 'MINUTOS' ? 1 : unidade === 'HORAS' ? 60 : unidade === 'DIAS_CORRIDOS' ? 1440 : NaN
  if (Number.isNaN(fator)) throw new Error(`Unidade ${unidade} não é uma duração contínua.`)
  return Math.round(meta * fator) * MINUTO
}

export function prazo(inicio: number, forma: FormaContagem, unidade: UnidadeMeta, meta: number, cal: Calculadora | null): number {
  if (unidade === 'PERCENTUAL') throw new Error('Indicador percentual não tem prazo.')
  if (forma === 'CORRIDO_24X7') return inicio + duracaoMeta(unidade, meta)
  if (!cal) throw new Error('Regra com contagem em horário comercial exige calendário.')
  if (unidade === 'DIAS_UTEIS') return cal.somarDiasUteis(inicio, Math.ceil(meta))
  return cal.somarTempoUtil(inicio, duracaoMeta(unidade, meta))
}

export interface Pausa {
  inicio: number
  fim: number | null
  motivo: string
}

function medir(de: number, ate: number, forma: FormaContagem, cal: Calculadora | null) {
  if (forma === 'CORRIDO_24X7' || !cal) return Math.max(0, ate - de)
  return cal.tempoUtil(de, ate)
}

export function avaliar(inicio: number, agora: number, pausas: Pausa[], forma: FormaContagem, unidade: UnidadeMeta,
                        meta: number, cal: Calculadora | null, limiar: number) {
  if (limiar < 1 || limiar > 99) throw new Error('O limiar de alerta deve estar entre 1% e 99%.')
  for (const p of pausas) if (!p.motivo?.trim()) throw new Error('Pausa de SLA exige motivo.')
  const prazoBase = prazo(inicio, forma, unidade, meta, cal)
  const metaMs = medir(inicio, prazoBase, forma, cal)
  let consumido = medir(inicio, agora, forma, cal)
  for (const p of pausas) {
    const de = Math.max(p.inicio, inicio)
    const ate = p.fim === null || p.fim > agora ? agora : p.fim
    if (ate > de) consumido -= medir(de, ate, forma, cal)
  }
  consumido = Math.max(0, consumido)
  const percentual = metaMs === 0 ? 100 : Math.min(999, Math.floor((consumido * 100) / Math.max(1, metaMs)))
  const situacao = consumido >= metaMs ? 'VIOLADO' : percentual >= limiar ? 'EM_RISCO' : 'EM_DIA'
  const restante = metaMs - consumido
  let prazoFinal = restante <= 0 ? agora : forma === 'CORRIDO_24X7' || !cal ? agora + restante : cal.somarTempoUtil(agora, restante)
  if (agora < inicio) prazoFinal = prazoBase
  return { prazo: prazoFinal, consumido, meta: metaMs, percentual, situacao: situacao as 'EM_DIA' | 'EM_RISCO' | 'VIOLADO' }
}
