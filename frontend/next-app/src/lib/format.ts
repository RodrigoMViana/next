// format.ts -- utilitário único de formatação em pt-BR (Arquitetura 12.11)
const locale = 'pt-BR'

/** Datas sem hora (aaaa-mm-dd) são exibidas sem conversão de fuso. */
export const fmtDate = (iso?: string | null): string => {
  if (!iso) return '—'
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) {
    const [a, m, d] = iso.split('-')
    return `${d}/${m}/${a}`
  }
  return new Intl.DateTimeFormat(locale, { dateStyle: 'short' }).format(new Date(iso))
}

export const fmtDateTime = (iso?: string | null): string =>
  iso ? new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso)) : '—'

export const fmtMoney = (v?: number | null): string =>
  v === null || v === undefined ? '—' : new Intl.NumberFormat(locale, { style: 'currency', currency: 'BRL' }).format(v)

export const fmtNumber = (v?: number | null): string =>
  v === null || v === undefined ? '—' : new Intl.NumberFormat(locale).format(v)

/** Converte "aaaa-mm-ddThh:mm" de um input datetime-local (horário local) para ISO UTC. */
export const localParaIso = (valor: string): string => new Date(valor).toISOString()

/** ISO para o formato do input datetime-local, no fuso do navegador. */
export const isoParaLocal = (iso: string): string => {
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

export const hojeIso = (): string => {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export const iniciais = (nome: string): string =>
  nome
    .split(/\s+/)
    .filter((p) => p.length > 2 || /^[A-ZÁÉÍÓÚ]/.test(p))
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('')

export const ROTULOS: Record<string, string> = {
  ATIVO: 'Ativo',
  DESATIVADO: 'Desativado',
  DESENVOLVIMENTO: 'Desenvolvimento',
  MANUTENCAO: 'Manutenção',
  SUSTENTACAO: 'Sustentação',
  RASCUNHO: 'Rascunho',
  EM_AVALIACAO: 'Em avaliação',
  VIGENTE: 'Vigente',
  ENCERRADA: 'Encerrada',
  CONTRATADA: 'Contratada',
  EQUIPE_INTERNA: 'Equipe interna',
  MINUTOS: 'minutos',
  HORAS: 'horas',
  DIAS_UTEIS: 'dias úteis',
  DIAS_CORRIDOS: 'dias corridos',
  PERCENTUAL: '%',
  CORRIDO_24X7: 'Corrido (24x7)',
  HORARIO_COMERCIAL: 'Horário comercial',
  GESTOR_CONTRATO: 'Gestor do contrato',
  FISCAL_TECNICO: 'Fiscal técnico',
  FISCAL_ADMINISTRATIVO: 'Fiscal administrativo',
  FISCAL_REQUISITANTE: 'Fiscal requisitante',
  N2_GESTOR: 'N2 gestor',
  N2_TECNICO: 'N2+ técnico',
  NACIONAL: 'Nacional',
  ESTADUAL: 'Estadual',
  MUNICIPAL: 'Municipal',
  PONTO_FACULTATIVO: 'Ponto facultativo',
  ADMINISTRADOR: 'Administrador',
  GESTOR_TI: 'Gestor de TI',
  N1: 'N1',
  FACILITADOR: 'Facilitador',
  RESPONSAVEL_AREA: 'Responsável da área',
  MEMBRO_COMITE: 'Membro de comitê',
  CLIENTE: 'Cliente / solicitante',
  EM_DIA: 'Em dia',
  EM_RISCO: 'Em risco',
  VIOLADO: 'Violado',
  PROCESSANDO: 'Processando',
  CONCLUIDA: 'Concluída',
  FALHOU: 'Falhou',
}

export const rotulo = (v?: string | null): string => (v ? (ROTULOS[v] ?? v) : '—')

export const DIAS_SEMANA = ['', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo']
