// Tipos do contrato da API do BFF (/api/v1). Espelham os DTOs do módulo Configuração.
export type Situacao = 'ATIVO' | 'DESATIVADO'
export type Uuid = string

export interface Pagina<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ErroCampo {
  field: string
  message: string
}

export interface ProblemDetails {
  type: string
  title: string
  status: number
  detail: string
  instance?: string
  correlationId?: string
  errors?: ErroCampo[]
}

export interface Sessao {
  id: string
  nome: string
  cpfMascarado: string
  perfis: string[]
  contratadaId: string | null
  usuarioDeFabrica: boolean
  acessaConfiguracao: boolean
  permissoes: Record<string, boolean>
  loginEm: string
  modoAutenticacao: string
}

export interface UsuarioDemo {
  id: string
  nome: string
  perfis: string[]
  descricao: string
  exigeMfa: boolean
}

export interface Ref {
  id: Uuid
  codigo: string
  nome: string
}

export interface Historico {
  id: Uuid
  entidade: string
  entidadeId: string
  acao: string
  campo: string | null
  valorAnterior: string | null
  valorNovo: string | null
  justificativa: string | null
  usuarioId: string
  usuarioNome: string
  ocorridoEm: string
  correlationId: string | null
}

export interface Base {
  id: Uuid
  situacao: Situacao
  justificativaSituacao: string | null
  version: number
  createdAt: string | null
  updatedAt: string | null
}

export interface UnidadeMedida extends Base {
  sigla: string
  nome: string
  descricao: string | null
}

export interface FonteRecurso extends Base {
  codigo: string
  nome: string
  descricao: string | null
}

export type TipoServico = 'DESENVOLVIMENTO' | 'MANUTENCAO' | 'SUSTENTACAO'

export interface Servico extends Base {
  codigo: string
  nome: string
  descricao: string | null
  tipo: TipoServico
  unidadeMedida: Ref
}

export interface Expediente {
  diaSemana: number
  horaInicio: string
  horaFim: string
}

export interface Feriado {
  id?: Uuid | null
  data: string
  descricao: string
  abrangencia: 'NACIONAL' | 'ESTADUAL' | 'MUNICIPAL' | 'PONTO_FACULTATIVO'
  recorrenteAnual: boolean
}

export interface Calendario extends Base {
  nome: string
  descricao: string | null
  fusoHorario: string
  vigenciaInicio: string
  expedientes: Expediente[]
  feriados: Feriado[]
  horasSemanais: number
}

export interface Contratada extends Base {
  cnpj: string
  cnpjFormatado: string
  cnpjAlfanumerico: boolean
  razaoSocial: string
  nomeFantasia: string | null
  contratosAtivos: number
}

export interface Membro {
  id: Uuid
  usuarioId: string
  nomeExibicao: string
  papel: 'N2_GESTOR' | 'N2_TECNICO'
  inicio: string
  fim: string | null
  vigente: boolean
  mfaObrigatorio: boolean
  version: number
}

export interface AlteracaoContratual {
  id: Uuid
  dataAlteracao: string
  tipo: string
  descricao: string
  anexoNome: string | null
  anexoTipo: string | null
  anexoTamanho: number | null
  createdAt: string
  createdByNome: string
}

export interface ValidacaoCnpj {
  entrada: string
  normalizado: string
  formatado: string | null
  valido: boolean
  alfanumerico: boolean
  mensagem: string | null
}

export interface ItemContrato {
  id: Uuid
  servico: Ref
  unidadeMedida: Ref
  volume: number
  valorUnitario: number
  valorTotal: number
}

export interface Aditivo {
  id: Uuid
  numero: string
  aditivoPrazo: boolean
  aditivoValor: boolean
  novaVigenciaFim: string | null
  valorAcrescimo: number | null
  dataAssinatura: string
  justificativa: string
  createdAt: string
  createdByNome: string
}

export type TipoFiscal = 'GESTOR_CONTRATO' | 'FISCAL_TECNICO' | 'FISCAL_ADMINISTRATIVO' | 'FISCAL_REQUISITANTE'

export interface Fiscal {
  id: Uuid
  usuarioId: string
  nomeExibicao: string
  tipoFiscal: TipoFiscal
  inicio: string
  fim: string | null
  vigente: boolean
  motivoEncerramento: string | null
  substituidoPorId: Uuid | null
}

export interface SistemaVinculado {
  sistemaId: Uuid
  sistemaNome: string
}

export interface ContratoResumo {
  id: Uuid
  numero: string
  objeto: string
  contratada: Ref
  vigenciaInicio: string
  vigenciaFim: string
  vigenciaFimEfetiva: string
  valorEfetivo: number
  vigente: boolean
  situacao: Situacao
}

export interface ContratoDetalhe extends Base {
  numero: string
  objeto: string
  contratada: Ref
  calendario: Ref
  vigenciaInicio: string
  vigenciaFim: string
  vigenciaFimEfetiva: string
  valorOriginal: number
  valorEfetivo: number
  vigente: boolean
  temFiscalVigente: boolean
  itens: ItemContrato[]
  fontes: Ref[]
  sistemas: SistemaVinculado[]
  aditivos: Aditivo[]
  fiscais: Fiscal[]
}

export type UnidadeMeta = 'MINUTOS' | 'HORAS' | 'DIAS_UTEIS' | 'DIAS_CORRIDOS' | 'PERCENTUAL'
export type FormaContagem = 'CORRIDO_24X7' | 'HORARIO_COMERCIAL'
export type SituacaoVersao = 'RASCUNHO' | 'EM_AVALIACAO' | 'VIGENTE' | 'ENCERRADA'

export interface RegraSla {
  id?: Uuid
  ordem?: number
  classe: string
  indicador: string
  metaValor: number
  metaUnidade: UnidadeMeta
  formaContagem: FormaContagem
  calendario?: Ref | null
  calendarioId?: Uuid | null
  permitePausa: boolean
  observacao: string | null
}

export interface VersaoSla {
  id: Uuid
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
  regras: RegraSla[]
  version: number
}

export interface SlaResumo {
  id: Uuid
  codigo: string
  nome: string
  tipoResponsavel: 'CONTRATADA' | 'EQUIPE_INTERNA'
  contrato: Ref | null
  contratadaNome: string | null
  equipeInterna: string | null
  versaoVigente: number | null
  vigenteDesde: string | null
  situacaoUltimaVersao: SituacaoVersao | null
  situacao: Situacao
}

export interface SlaDetalhe extends Base {
  codigo: string
  nome: string
  tipoResponsavel: 'CONTRATADA' | 'EQUIPE_INTERNA'
  contrato: Ref | null
  contratadaId: Uuid | null
  contratadaNome: string | null
  equipeInterna: string | null
  servicos: Ref[]
  sistemas: SistemaVinculado[]
  versoes: VersaoSla[]
}

export interface SimulacaoSla {
  inicio: string
  agora: string
  prazo: string
  minutosConsumidos: number
  minutosMeta: number
  percentualConsumido: number
  situacao: 'EM_DIA' | 'EM_RISCO' | 'VIOLADO'
}

export interface EventoCatalogo {
  evento: string
  modulo: string
  descricao: string
  variaveis: string[]
  exigeLimiar: boolean
}

export interface Notificacao extends Base {
  nome: string
  evento: string
  canalApp: boolean
  canalEmail: boolean
  perfis: string[]
  assunto: string
  modelo: string
  limiarPercentual: number | null
}

export interface SistemaPortfolio {
  id: Uuid
  sigla: string
  nome: string
  situacao: string
  areaResponsavel: string
  contratos: Ref[]
  slas: Ref[]
  temSlaVigente: boolean
  pendenciaConfiguracao: boolean
}

export interface Pendencia {
  tipo: string
  descricao: string
  recurso: string
  recursoId: Uuid | null
}

export interface Painel {
  totais: Record<string, number>
  pendencias: Pendencia[]
  eventosPendentes: number
  portfolioDisponivel: boolean
  dataReferencia: string
}

export interface UsuarioCore {
  id: string
  nomeExibicao: string
  perfis: string[]
  contratadaId: string | null
  mfaAtivo: boolean
}

export interface RegistroAcesso {
  id: string
  ocorridoEm: string
  usuarioId: string | null
  usuarioNome: string | null
  perfis: string | null
  metodo: string
  rota: string
  modulo: string
  recurso: string | null
  acao: string
  status: number
  ip: string | null
  correlationId: string | null
  dadoPessoal: boolean
  duracaoMs: number | null
}

export interface Exportacao {
  id: string
  tipo: 'ACESSOS' | 'GOVERNANCA'
  filtros: string | null
  situacao: 'PROCESSANDO' | 'CONCLUIDA' | 'FALHOU'
  criadoEm: string
  concluidoEm: string | null
  totalRegistros: number | null
  expiraEm: string | null
  mensagemErro: string | null
}
