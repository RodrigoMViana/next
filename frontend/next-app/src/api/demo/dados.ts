/**
 * Estado do modo demonstração, guardado no localStorage do navegador (somente dados fictícios).
 * Estruturas próximas das tabelas do módulo (next-configuracao) para facilitar a comparação.
 */
import type {
  AlteracaoContratual, Aditivo, Calendario, Contratada, Exportacao, FonteRecurso, Historico, Membro, Notificacao,
  RegistroAcesso, Situacao, SituacaoVersao, TipoFiscal, TipoServico, UnidadeMedida, UnidadeMeta, FormaContagem,
} from '../tipos'

export interface Controle {
  id: string
  situacao: Situacao
  justificativaSituacao: string | null
  version: number
  createdAt: string | null
  updatedAt: string | null
}

export interface ServicoDb extends Controle {
  codigo: string
  nome: string
  descricao: string | null
  tipo: TipoServico
  unidadeMedidaId: string
}

export interface ContratoDb extends Controle {
  numero: string
  objeto: string
  contratadaId: string
  calendarioId: string
  vigenciaInicio: string
  vigenciaFim: string
  itens: Array<{ id: string; servicoId: string; unidadeMedidaId: string; volume: number; valorUnitario: number; valorTotal: number }>
  fonteIds: string[]
  sistemas: Array<{ sistemaId: string; sistemaNome: string }>
}

export interface FiscalDb {
  id: string
  contratoId: string
  usuarioId: string
  nomeExibicao: string
  tipoFiscal: TipoFiscal
  inicio: string
  fim: string | null
  motivoEncerramento: string | null
  substituidoPorId: string | null
}

export interface RegraDb {
  id: string
  ordem: number
  classe: string
  indicador: string
  metaValor: number
  metaUnidade: UnidadeMeta
  formaContagem: FormaContagem
  calendarioId: string | null
  permitePausa: boolean
  observacao: string | null
}

export interface VersaoDb {
  id: string
  slaId: string
  numero: number
  situacao: SituacaoVersao
  vigenciaInicio: string | null
  vigenciaFim: string | null
  motivoAlteracao: string | null
  parecerFavoravel: boolean | null
  parecerTexto: string | null
  parecerPorNome: string | null
  parecerEm: string | null
  decisaoObservacao: string | null
  decisaoPorNome: string | null
  decisaoEm: string | null
  regras: RegraDb[]
  version: number
}

export interface SlaDb extends Controle {
  codigo: string
  nome: string
  tipoResponsavel: 'CONTRATADA' | 'EQUIPE_INTERNA'
  contratoId: string | null
  contratadaId: string | null
  equipeInterna: string | null
  servicoIds: string[]
  sistemas: Array<{ sistemaId: string; sistemaNome: string }>
}

export interface SessaoDb {
  id: string
  nome: string
  cpf: string
  perfis: string[]
  contratadaId: string | null
  loginEm: string
}

export interface Banco {
  versaoEsquema: number
  unidades: UnidadeMedida[]
  fontes: FonteRecurso[]
  servicos: ServicoDb[]
  calendarios: Calendario[]
  contratadas: Array<Omit<Contratada, 'cnpjFormatado' | 'cnpjAlfanumerico' | 'contratosAtivos'>>
  membros: Array<Membro & { contratadaId: string }>
  alteracoes: Array<AlteracaoContratual & { contratadaId: string; anexoBase64: string | null }>
  contratos: ContratoDb[]
  aditivos: Array<Aditivo & { contratoId: string }>
  fiscais: FiscalDb[]
  slas: SlaDb[]
  versoes: VersaoDb[]
  notificacoes: Notificacao[]
  historico: Historico[]
  trilha: RegistroAcesso[]
  exportacoes: Array<Exportacao & { solicitanteId: string; conteudo: string | null; tokenLink: string | null }>
  idempotencia: Record<string, string>
  eventosProcessados: string[]
  sessao: SessaoDb | null
}

export const CHAVE = 'next-demo-banco-v1'
export const VERSAO_ESQUEMA = 1

export function carregar(): Banco | null {
  try {
    const t = localStorage.getItem(CHAVE)
    if (!t) return null
    const b = JSON.parse(t) as Banco
    return b.versaoEsquema === VERSAO_ESQUEMA ? b : null
  } catch {
    return null
  }
}

export function salvar(b: Banco) {
  try {
    if (b.trilha.length > 3000) b.trilha.splice(0, b.trilha.length - 3000)
    localStorage.setItem(CHAVE, JSON.stringify(b))
  } catch {
    /* armazenamento indisponível: segue em memória */
  }
}

export function apagar() {
  try {
    localStorage.removeItem(CHAVE)
  } catch {
    /* ignora */
  }
}

export const uuid = (): string =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx'.replace(/x/g, () => Math.floor(Math.random() * 16).toString(16))
