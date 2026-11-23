/**
 * BFF simulado no navegador (modo demonstração / GitHub Pages).
 *
 * Reproduz as rotas /api/v1 do next-bff e as regras do módulo next-configuracao (RN01 a RN29) para que
 * a ferramenta possa ser avaliada sem servidor. Os dados são fictícios e ficam no localStorage.
 * Em produção o front-end usa o transporte HTTP (cliente.ts) e todas as regras rodam no Java.
 */
import type { Opcoes, Resposta, Transporte } from '../cliente'
import { ProblemaError } from '../cliente'
import type {
  Calendario, ContratoDetalhe, ContratoResumo, Expediente, Feriado, Historico, Notificacao, Pagina, ProblemDetails,
  RegistroAcesso, RegraSla, SlaDetalhe, SlaResumo, UsuarioCore, VersaoSla,
} from '../tipos'
import { calcularDigitosCnpj, formatarCnpj, normalizarCnpj, validarCnpj } from '../../lib/cnpj'
import { avaliar, Calculadora, prazo as calcularPrazo, validarRegra, type Pausa } from './regras'
import { apagar, carregar, salvar, uuid, type Banco, type ContratoDb, type FiscalDb, type RegraDb, type SessaoDb, type SlaDb, type VersaoDb } from './dados'

// ---------------------------------------------------------------- dados de referência fictícios
const FABRICA_ALFA = 'a1fa0000-0000-4000-8000-000000000001'
const FABRICA_BETA = 'be7a0000-0000-4000-8000-000000000002'

export const USUARIOS_DEMO = [
  { id: '10000000-0000-4000-8000-000000000001', nome: 'Ana Administradora', cpf: '52998224725', perfis: ['ADMINISTRADOR'], contratadaId: null, descricao: 'Administrador: parâmetros e todas as telas' },
  { id: '10000000-0000-4000-8000-000000000002', nome: 'Gustavo Gestor de TI', cpf: '11144477735', perfis: ['GESTOR_TI'], contratadaId: null, descricao: 'Gestor de TI: cadastra, decide vigência de SLA e consulta auditoria' },
  { id: '10000000-0000-4000-8000-000000000003', nome: 'Nina Atendente N1', cpf: '39053344705', perfis: ['N1'], contratadaId: null, descricao: 'N1: leitura dos cadastros e do Portfólio' },
  { id: '10000000-0000-4000-8000-000000000004', nome: 'Fábio Gestor (Fábrica Alfa)', cpf: '71428793860', perfis: ['N2_GESTOR'], contratadaId: FABRICA_ALFA, descricao: 'N2 gestor: vê só a Fábrica Alfa e dá parecer no SLA (MFA)' },
  { id: '10000000-0000-4000-8000-000000000006', nome: 'Bruno Gestor (Fábrica Beta)', cpf: '48527370000', perfis: ['N2_GESTOR'], contratadaId: FABRICA_BETA, descricao: 'N2 gestor: vê só a Fábrica Beta (MFA)' },
  { id: '10000000-0000-4000-8000-000000000005', nome: 'Tânia Técnica (Fábrica Alfa)', cpf: '86288366757', perfis: ['N2_TECNICO'], contratadaId: FABRICA_ALFA, descricao: 'N2+ técnico: sem acesso a este módulo (MFA)' },
  { id: '10000000-0000-4000-8000-000000000009', nome: 'Caio Cliente', cpf: '15350946056', perfis: ['CLIENTE'], contratadaId: null, descricao: 'Cliente/solicitante: sem acesso a este módulo' },
]

const USUARIOS_CORE: UsuarioCore[] = [
  ...USUARIOS_DEMO.map((u) => ({ id: u.id, nomeExibicao: u.nome, perfis: u.perfis, contratadaId: u.contratadaId, mfaAtivo: u.perfis.some((p) => p.startsWith('N2')) })),
  { id: '10000000-0000-4000-8000-000000000007', nomeExibicao: 'Carla Fiscal Técnica', perfis: ['FACILITADOR'], contratadaId: null, mfaAtivo: false },
  { id: '10000000-0000-4000-8000-000000000008', nomeExibicao: 'Rui Fiscal Administrativo', perfis: ['FACILITADOR'], contratadaId: null, mfaAtivo: false },
  { id: '10000000-0000-4000-8000-000000000010', nomeExibicao: 'Sofia Fiscal Requisitante', perfis: ['RESPONSAVEL_AREA'], contratadaId: null, mfaAtivo: false },
]

const SISTEMAS = [
  { id: '5a000000-0000-4000-8000-000000000001', sigla: 'SIARR', nome: 'Sistema de Arrecadação', situacao: 'ATIVO', areaResponsavel: 'Receita' },
  { id: '5a000000-0000-4000-8000-000000000002', sigla: 'PROT', nome: 'Protocolo Eletrônico', situacao: 'ATIVO', areaResponsavel: 'Administração' },
  { id: '5a000000-0000-4000-8000-000000000003', sigla: 'PCID', nome: 'Portal do Cidadão', situacao: 'ATIVO', areaResponsavel: 'Atendimento' },
  { id: '5a000000-0000-4000-8000-000000000004', sigla: 'FOLHA', nome: 'Folha de Pagamento', situacao: 'ATIVO', areaResponsavel: 'Gestão de Pessoas' },
  { id: '5a000000-0000-4000-8000-000000000005', sigla: 'ALMOX', nome: 'Controle de Almoxarifado', situacao: 'ATIVO', areaResponsavel: 'Logística' },
  { id: '5a000000-0000-4000-8000-000000000006', sigla: 'SGL', nome: 'Sistema Legado de Guias', situacao: 'DESCONTINUADO', areaResponsavel: 'Receita' },
]

const PERFIS = ['ADMINISTRADOR', 'GESTOR_TI', 'FACILITADOR', 'RESPONSAVEL_AREA', 'MEMBRO_COMITE', 'N1', 'N2_GESTOR', 'N2_TECNICO', 'CLIENTE']

const CATALOGO = [
  { evento: 'configuracao.contrato.criado', modulo: 'Configuração', descricao: 'Contrato cadastrado', variaveis: ['contrato.numero', 'contratada.razaoSocial', 'contrato.vigenciaFim', 'usuario.nome'], exigeLimiar: false },
  { evento: 'configuracao.contrato.vencendo', modulo: 'Configuração', descricao: 'Contrato próximo do fim da vigência', variaveis: ['contrato.numero', 'contratada.razaoSocial', 'contrato.vigenciaFim', 'dias'], exigeLimiar: false },
  { evento: 'configuracao.aditivo.registrado', modulo: 'Configuração', descricao: 'Aditivo registrado em contrato', variaveis: ['contrato.numero', 'aditivo.numero', 'contrato.vigenciaFim', 'contrato.valor'], exigeLimiar: false },
  { evento: 'configuracao.fiscal.substituido', modulo: 'Configuração', descricao: 'Fiscal do contrato substituído', variaveis: ['contrato.numero', 'fiscal.anterior', 'fiscal.novo', 'data'], exigeLimiar: false },
  { evento: 'configuracao.sla.em-avaliacao', modulo: 'Configuração', descricao: 'Versão de SLA enviada para avaliação', variaveis: ['sla.codigo', 'sla.nome', 'sla.versao', 'contrato.numero'], exigeLimiar: false },
  { evento: 'configuracao.sla.vigente', modulo: 'Configuração', descricao: 'Versão de SLA passou a vigorar', variaveis: ['sla.codigo', 'sla.nome', 'sla.versao', 'sla.vigenciaInicio'], exigeLimiar: false },
  { evento: 'configuracao.sistema.pendente-configuracao', modulo: 'Configuração', descricao: 'Sistema ativado no Portfólio sem SLA vigente', variaveis: ['sistema.nome', 'sistema.sigla'], exigeLimiar: false },
  { evento: 'sustentacao.sla.em-risco', modulo: 'Sustentação', descricao: 'SLA de chamado em risco', variaveis: ['chamado.codigo', 'sla.codigo', 'sla.prazo', 'sla.percentual', 'fila.nome'], exigeLimiar: true },
  { evento: 'sustentacao.sla.violado', modulo: 'Sustentação', descricao: 'SLA de chamado violado', variaveis: ['chamado.codigo', 'sla.codigo', 'sla.prazo', 'fila.nome'], exigeLimiar: false },
  { evento: 'portfolio.sistema.ativado', modulo: 'Portfólio', descricao: 'Sistema ativado no Portfólio', variaveis: ['sistema.nome', 'sistema.sigla'], exigeLimiar: false },
]

const EXEMPLOS: Record<string, string> = {
  'contrato.numero': 'CT-2026-0007', 'contratada.razaoSocial': 'Fábrica Alfa Tecnologia Ltda.', 'contrato.vigenciaFim': '31/12/2027',
  'sla.vigenciaInicio': '31/12/2027', data: '31/12/2027', 'contrato.valor': 'R$ 1.234.567,89', dias: '30', 'aditivo.numero': '2º TA',
  'fiscal.anterior': 'Rui Fiscal', 'fiscal.novo': 'Carla Fiscal', 'sla.codigo': 'SLA-ALFA', 'sla.nome': 'SLA de sustentação Alfa',
  'sla.versao': '3', 'sla.prazo': '25/11/2026 14:30', 'sla.percentual': '80%', 'chamado.codigo': 'SUP-2041',
  'fila.nome': 'N2 - Fábrica Alfa', 'sistema.nome': 'Sistema de Arrecadação', 'sistema.sigla': 'SIARR', 'usuario.nome': 'Gustavo Gestor',
}

// ---------------------------------------------------------------- utilitários
const GESTAO = ['ADMINISTRADOR', 'GESTOR_TI']
const LEITURA = ['ADMINISTRADOR', 'GESTOR_TI', 'N1', 'N2_GESTOR']
const LEITURA_PORTFOLIO = ['ADMINISTRADOR', 'GESTOR_TI', 'N1']
const ID = '[0-9a-fA-F-]{36}'
const REGRAS_RBAC: Array<[string, RegExp, string[]]> = [
  ['POST', new RegExp(`^/slas/${ID}/versoes/${ID}/parecer$`), ['N2_GESTOR']],
  ['POST', new RegExp(`^/(slas|calendarios)/${ID}/simulacoes$`), LEITURA],
  ['GET', /^\/portfolio\/.*$/, LEITURA_PORTFOLIO],
  ['*', /^\/notificacoes(\/.*)?$/, GESTAO],
  ['GET', /^\/auditoria\/.*$/, GESTAO],
  ['GET', /^\/usuarios$/, GESTAO],
  ['POST', /^\/eventos$/, ['ADMINISTRADOR']],
  ['GET', /^\/(painel|contratos|contratadas|slas|servicos|unidades-medida|fontes-recurso|calendarios)(\/.*)?$/, LEITURA],
  ['POST,PUT,PATCH,DELETE', /^\/(contratos|contratadas|slas|servicos|unidades-medida|fontes-recurso|calendarios)(\/.*)?$/, GESTAO],
]

class Erro extends Error {
  constructor(readonly status: number, readonly codigo: string, readonly titulo: string, detalhe: string, readonly campo?: string) {
    super(detalhe)
  }
}
const erroCampo = (campo: string, msg: string) => new Erro(422, 'validacao', 'Dados inválidos', msg, campo)
const naoEncontrado = (recurso: string) => new Erro(404, 'recurso-nao-encontrado', 'Registro não encontrado', `${recurso} não existe ou não está disponível para o seu perfil.`)
const proibido = (msg = 'Seu perfil não permite esta operação.') => new Erro(403, 'acesso-negado', 'Acesso negado', msg)
const conflito = (campo: string, msg: string) => new Erro(409, 'conflito', 'Registro duplicado', msg, campo)

function regra<T>(campo: string, fn: () => T): T {
  try {
    return fn()
  } catch (e) {
    if (e instanceof Erro) throw e
    throw new Erro(422, 'regra-de-negocio', 'Regra de negócio não atendida', (e as Error).message, campo)
  }
}

const agoraIso = () => new Date().toISOString()
function hoje(): string {
  const p = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
  return p
}
const somarDias = (iso: string, dias: number) => {
  const d = new Date(iso + 'T00:00:00Z')
  d.setUTCDate(d.getUTCDate() + dias)
  return d.toISOString().slice(0, 10)
}
const texto = (v: unknown) => (typeof v === 'string' && v.trim() ? v.trim() : null)
const minusc = (s: string | null | undefined) => (s ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
const contem = (alvo: string | null | undefined, termo: string) => minusc(alvo).includes(minusc(termo))
const centavos = (v: number) => Math.round(v * 100) / 100
const vigenteEm = (data: string, inicio: string, fim: string | null) => data >= inicio && (fim === null || data <= fim)

function paginar<T>(lista: T[], q: URLSearchParams, ordens: Record<string, (x: T) => string | number>, padrao: string): Pagina<T> {
  const page = Math.max(0, Number(q.get('page') ?? 0) || 0)
  const size = Math.min(100, Math.max(1, Number(q.get('size') ?? 20) || 20))
  const [campo, dir] = (q.get('sort') ?? padrao).split(',')
  const f = ordens[campo!] ?? ordens[padrao.split(',')[0]!]!
  const ordenada = [...lista].sort((a, b) => {
    const x = f(a), y = f(b)
    const r = typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y), 'pt-BR')
    return dir === 'desc' ? -r : r
  })
  return { content: ordenada.slice(page * size, page * size + size), page, size, totalElements: lista.length, totalPages: Math.ceil(lista.length / size) }
}

// ---------------------------------------------------------------- o BFF simulado
export class BffDemo implements Transporte {
  readonly modo = 'demo' as const
  private b: Banco

  constructor() {
    this.b = carregar() ?? this.novoBanco()
  }

  restaurar() {
    apagar()
    this.b = this.novoBanco()
  }

  private novoBanco(): Banco {
    this.b = {
      versaoEsquema: 1, unidades: [], fontes: [], servicos: [], calendarios: [], contratadas: [], membros: [], alteracoes: [],
      contratos: [], aditivos: [], fiscais: [], slas: [], versoes: [], notificacoes: [], historico: [], trilha: [],
      exportacoes: [], idempotencia: {}, eventosProcessados: [], sessao: null,
    }
    this.semear()
    this.b.sessao = null
    this.b.trilha = []
    salvar(this.b)
    return this.b
  }

  // ------------------------------------------------------------ entrada única
  async requisitar<T>(metodo: string, caminho: string, op: Opcoes = {}, binario = false): Promise<Resposta<T>> {
    await new Promise((r) => setTimeout(r, 120))
    // Relê o estado a cada requisição: várias abas do navegador compartilham os mesmos dados
    this.b = carregar() ?? this.b
    const correlationId = 'c-' + uuid().slice(0, 8)
    const inicio = performance.now()
    const q = new URLSearchParams()
    for (const [k, v] of Object.entries(op.query ?? {})) if (v !== null && v !== undefined && v !== '') q.set(k, String(v))
    let status = 200
    try {
      const r = this.rotear(metodo, caminho, q, op)
      status = r.status
      if (binario && r.blob) return { status: r.status, dados: null as T, cabecalhos: {}, blob: r.blob }
      return { status: r.status, dados: r.dados as T, etag: r.etag, cabecalhos: r.cabecalhos ?? {} }
    } catch (e) {
      const err = e instanceof Erro ? e : new Erro(500, 'erro-interno', 'Erro interno', 'Não foi possível concluir a operação.')
      if (!(e instanceof Erro)) console.error(e)
      status = err.status
      const p: ProblemDetails = { type: 'urn:next:erro:' + err.codigo, title: err.titulo, status: err.status, detail: err.message,
        instance: caminho, correlationId, errors: err.campo ? [{ field: err.campo, message: err.message }] : [] }
      throw new ProblemaError(p)
    } finally {
      this.trilhar(metodo, caminho, status, correlationId, Math.round(performance.now() - inicio))
      salvar(this.b)
    }
  }

  private trilhar(metodo: string, rota: string, status: number, correlationId: string, duracaoMs: number) {
    if (rota === '/api/v1/auth/sessao' || rota === '/api/v1/auth/csrf' || rota === '/api/v1/auth/usuarios-demo') return
    const u = this.b.sessao
    const partes = rota.split('/')
    const acao = metodo === 'GET' ? (rota.includes('/arquivo') ? 'DOWNLOAD' : 'LEITURA') : metodo === 'POST'
      ? (rota.endsWith('/login') ? 'LOGIN' : rota.endsWith('/logout') ? 'LOGOUT' : rota.includes('/exportacoes') ? 'EXPORTACAO' : 'ESCRITA')
      : metodo === 'PUT' ? 'ALTERACAO' : 'REMOCAO'
    const reg: RegistroAcesso = { id: uuid(), ocorridoEm: agoraIso(), usuarioId: u?.id ?? null, usuarioNome: u?.nome ?? null,
      perfis: u?.perfis.join(',') ?? null, metodo, rota, modulo: partes[3] ?? 'bff', recurso: partes[4] ?? null, acao, status,
      ip: '127.0.0.1 (demo)', correlationId, duracaoMs,
      dadoPessoal: /usuarios|equipe|fiscais|auditoria|historico|auth\/login/.test(rota) || /^\/api\/v1\/configuracao\/contratos\/[^/]+$/.test(rota) }
    this.b.trilha.push(reg)
  }

  private rotear(metodo: string, caminho: string, q: URLSearchParams, op: Opcoes): { status: number; dados?: unknown; etag?: string; cabecalhos?: Record<string, string>; blob?: Blob } {
    const c = op.corpo as Record<string, unknown> | undefined
    // ---- autenticação
    if (caminho === '/api/v1/auth/csrf') return { status: 200, dados: { headerName: 'X-XSRF-TOKEN', token: 'demo' } }
    if (caminho === '/api/v1/auth/usuarios-demo') return { status: 200, dados: USUARIOS_DEMO.map((u) => ({ id: u.id, nome: u.nome, perfis: u.perfis, descricao: u.descricao, exigeMfa: u.perfis.some((p) => p.startsWith('N2')) })) }
    if (caminho === '/api/v1/auth/login' && metodo === 'POST') return { status: 200, dados: this.login(c ?? {}) }
    if (caminho === '/api/v1/auth/logout') {
      this.b.sessao = null
      return { status: 204 }
    }
    const u = this.b.sessao
    if (caminho === '/api/v1/auth/sessao') {
      if (!u) throw new Erro(401, 'nao-autenticado', 'Sessão inexistente', 'Entre para continuar.')
      return { status: 200, dados: this.saidaSessao(u) }
    }
    if (!u) throw new Erro(401, 'nao-autenticado', 'Sessão expirada ou inexistente', 'Entre novamente para continuar.')

    // ---- auditoria do BFF
    if (caminho.startsWith('/api/v1/auditoria')) return this.auditoria(metodo, caminho.slice('/api/v1/auditoria'.length), q, c, u)

    // ---- módulo Configuração (RBAC grosso como no BFF)
    const prefixo = '/api/v1/configuracao'
    if (!caminho.startsWith(prefixo)) throw new Erro(404, 'rota-inexistente', 'Rota inexistente', 'A rota solicitada não existe.')
    const rota = caminho.slice(prefixo.length)
    const permitidos = REGRAS_RBAC.find(([m, re]) => (m === '*' || m.includes(metodo)) && re.test(rota))?.[2] ?? []
    if (!u.perfis.some((p) => permitidos.includes(p))) throw proibido('Seu perfil não permite acessar esta funcionalidade.')
    return this.modulo(metodo, rota, q, op, u)
  }

  private login(c: Record<string, unknown>) {
    const dev = USUARIOS_DEMO.find((x) => x.id === c.usuarioId)
    if (!dev) throw new Erro(401, 'credenciais-invalidas', 'Usuário não encontrado', 'Usuário inexistente.', 'usuarioId')
    if (dev.perfis.some((p) => p.startsWith('N2'))) {
      if (!texto(c.codigoMfa)) throw new Erro(401, 'mfa-obrigatorio', 'MFA obrigatório', 'Usuários de fábrica precisam informar o código de autenticação multifator.', 'codigoMfa')
      if (c.codigoMfa !== '123456') throw new Erro(401, 'mfa-invalido', 'Código MFA inválido', 'O código informado não confere.', 'codigoMfa')
    }
    this.b.sessao = { id: dev.id, nome: dev.nome, cpf: dev.cpf, perfis: dev.perfis, contratadaId: dev.contratadaId, loginEm: agoraIso() }
    return this.saidaSessao(this.b.sessao)
  }

  private saidaSessao(u: SessaoDb) {
    const tem = (l: string[]) => u.perfis.some((p) => l.includes(p))
    const d = u.cpf.replace(/\D/g, '')
    return { id: u.id, nome: u.nome, cpfMascarado: `***.${d.slice(3, 6)}.${d.slice(6, 9)}-**`, perfis: u.perfis, contratadaId: u.contratadaId,
      usuarioDeFabrica: u.perfis.some((p) => p.startsWith('N2')), acessaConfiguracao: tem(LEITURA),
      permissoes: { escreverCadastros: tem(GESTAO), lerCadastros: tem(LEITURA), lerPortfolio: tem(LEITURA_PORTFOLIO), notificacoes: tem(GESTAO),
        auditoria: tem(GESTAO), parecerSla: u.perfis.includes('N2_GESTOR'), simularEventos: u.perfis.includes('ADMINISTRADOR') },
      loginEm: u.loginEm, modoAutenticacao: 'demo' }
  }

  // ------------------------------------------------------------ histórico (RF16)
  private hist(u: SessaoDb | null, entidade: string, id: string, acao: string, campo: string | null, antes: string | null, depois: string | null, just: string | null = null) {
    const h: Historico = { id: uuid(), entidade, entidadeId: id, acao, campo, valorAnterior: antes, valorNovo: depois, justificativa: just,
      usuarioId: u?.id ?? '00000000-0000-0000-0000-000000000000', usuarioNome: u?.nome ?? 'Sistema', ocorridoEm: agoraIso(), correlationId: null }
    this.b.historico.push(h)
  }

  private criacao(u: SessaoDb, entidade: string, id: string, snap: Record<string, unknown>) {
    const resumo = Object.entries(snap).filter(([, v]) => v !== null && v !== undefined && v !== '').map(([k, v]) => `${k}: ${v}`).join(' | ')
    this.hist(u, entidade, id, 'CRIACAO', null, null, resumo)
  }

  private alteracao(u: SessaoDb, entidade: string, id: string, antes: Record<string, unknown>, depois: Record<string, unknown>) {
    for (const k of new Set([...Object.keys(antes), ...Object.keys(depois)])) {
      const a = antes[k] == null || antes[k] === '' ? null : String(antes[k])
      const d = depois[k] == null || depois[k] === '' ? null : String(depois[k])
      if (a !== d) this.hist(u, entidade, id, 'ALTERACAO', k, a, d)
    }
  }

  private historicoDe(entidade: string, id: string) {
    return this.b.historico.filter((h) => h.entidade === entidade && h.entidadeId === id).sort((a, b) => b.ocorridoEm.localeCompare(a.ocorridoEm))
  }

  private verificarVersao(atual: number, ifMatch?: string) {
    if (!ifMatch) throw new Erro(428, 'if-match-obrigatorio', 'Cabeçalho If-Match obrigatório', 'Edições exigem o cabeçalho If-Match com a versão (ETag) do registro.')
    if (ifMatch.replace(/"/g, '') !== String(atual)) throw new Erro(412, 'versao-desatualizada', 'Registro alterado por outra pessoa', 'O registro foi alterado depois que você o abriu. Recarregue a página e repita a alteração.')
  }

  private tocar(x: { version: number; updatedAt: string | null }) {
    x.version++
    x.updatedAt = agoraIso()
  }

  private mudarSituacao(u: SessaoDb, entidade: string, x: { id: string; situacao: string; justificativaSituacao: string | null; version: number; updatedAt: string | null }, ativar: boolean, just: string | null) {
    if (ativar && x.situacao === 'ATIVO') throw erroCampo('situacao', 'O registro já está ativo.')
    if (!ativar && x.situacao === 'DESATIVADO') throw erroCampo('situacao', 'O registro já está desativado.')
    x.situacao = ativar ? 'ATIVO' : 'DESATIVADO'
    x.justificativaSituacao = just
    this.tocar(x)
    this.hist(u, entidade, x.id, ativar ? 'REATIVACAO' : 'DESATIVACAO', null, null, `Situação: ${x.situacao}`, just)
  }

  private restrito(u: SessaoDb) {
    return u.perfis.includes('N2_GESTOR') && !u.perfis.some((p) => ['ADMINISTRADOR', 'GESTOR_TI', 'N1'].includes(p))
  }

  // ------------------------------------------------------------ cálculos de contrato (RN01, RN02, RN05)
  private aditivosDe(cid: string) {
    return this.b.aditivos.filter((a) => a.contratoId === cid).sort((a, b) => a.dataAssinatura.localeCompare(b.dataAssinatura) || a.createdAt.localeCompare(b.createdAt))
  }
  private valorOriginal(c: ContratoDb) {
    return centavos(c.itens.reduce((s, i) => s + i.valorTotal, 0))
  }
  private valorEfetivo(c: ContratoDb) {
    return centavos(this.valorOriginal(c) + this.aditivosDe(c.id).reduce((s, a) => s + (a.aditivoValor ? (a.valorAcrescimo ?? 0) : 0), 0))
  }
  private fimEfetivo(c: ContratoDb) {
    const prazos = this.aditivosDe(c.id).filter((a) => a.aditivoPrazo && a.novaVigenciaFim)
    return prazos.length ? prazos[prazos.length - 1]!.novaVigenciaFim! : c.vigenciaFim
  }
  private utilizavel(c: ContratoDb, data = hoje()) {
    return c.situacao === 'ATIVO' && data >= c.vigenciaInicio && data <= this.fimEfetivo(c)
  }
  private fiscaisDe(cid: string) {
    return this.b.fiscais.filter((f) => f.contratoId === cid).sort((a, b) => b.inicio.localeCompare(a.inicio))
  }

  private calc(calId: string | null): Calculadora | null {
    const cal = this.b.calendarios.find((x) => x.id === calId)
    return cal ? new Calculadora(cal.fusoHorario, cal.expedientes, cal.feriados) : null
  }

  // ------------------------------------------------------------ saídas
  private contratadaSaida(x: Banco['contratadas'][number]) {
    return { ...x, cnpjFormatado: formatarCnpj(x.cnpj), cnpjAlfanumerico: !/^\d+$/.test(x.cnpj),
      contratosAtivos: this.b.contratos.filter((c) => c.contratadaId === x.id && c.situacao === 'ATIVO').length }
  }
  private ref(lista: Array<{ id: string; codigo?: string; sigla?: string; nome?: string; razaoSocial?: string; fusoHorario?: string }>, id: string) {
    const x = lista.find((y) => y.id === id)
    return { id, codigo: x?.codigo ?? x?.sigla ?? x?.fusoHorario ?? '', nome: x?.nome ?? x?.razaoSocial ?? '' }
  }
  private servicoSaida(s: Banco['servicos'][number]) {
    const um = this.b.unidades.find((x) => x.id === s.unidadeMedidaId)!
    return { ...s, unidadeMedida: { id: um.id, codigo: um.sigla, nome: um.nome } }
  }
  private contratoResumo(c: ContratoDb): ContratoResumo {
    const k = this.b.contratadas.find((x) => x.id === c.contratadaId)!
    return { id: c.id, numero: c.numero, objeto: c.objeto, contratada: { id: k.id, codigo: formatarCnpj(k.cnpj), nome: k.razaoSocial },
      vigenciaInicio: c.vigenciaInicio, vigenciaFim: c.vigenciaFim, vigenciaFimEfetiva: this.fimEfetivo(c), valorEfetivo: this.valorEfetivo(c),
      vigente: this.utilizavel(c), situacao: c.situacao }
  }
  private contratoDetalhe(c: ContratoDb): ContratoDetalhe {
    const k = this.b.contratadas.find((x) => x.id === c.contratadaId)!
    const cal = this.b.calendarios.find((x) => x.id === c.calendarioId)!
    const h = hoje()
    return { ...this.contratoResumo(c), contratada: { id: k.id, codigo: formatarCnpj(k.cnpj), nome: k.razaoSocial },
      calendario: { id: cal.id, codigo: cal.fusoHorario, nome: cal.nome }, valorOriginal: this.valorOriginal(c),
      temFiscalVigente: this.fiscaisDe(c.id).some((f) => vigenteEm(h, f.inicio, f.fim)),
      itens: c.itens.map((i) => {
        const s = this.b.servicos.find((x) => x.id === i.servicoId)!
        const um = this.b.unidades.find((x) => x.id === i.unidadeMedidaId)!
        return { id: i.id, servico: { id: s.id, codigo: s.codigo, nome: s.nome }, unidadeMedida: { id: um.id, codigo: um.sigla, nome: um.nome },
          volume: i.volume, valorUnitario: i.valorUnitario, valorTotal: i.valorTotal }
      }),
      fontes: c.fonteIds.map((id) => this.ref(this.b.fontes, id)).sort((a, b) => a.codigo.localeCompare(b.codigo)),
      sistemas: c.sistemas, aditivos: this.aditivosDe(c.id).map(({ contratoId: _c, ...a }) => a),
      fiscais: this.fiscaisDe(c.id).map(({ contratoId: _c, ...f }) => ({ ...f, vigente: vigenteEm(h, f.inicio, f.fim) })),
      justificativaSituacao: c.justificativaSituacao, version: c.version, createdAt: c.createdAt, updatedAt: c.updatedAt }
  }
  private versaoSaida(v: VersaoDb): VersaoSla {
    return { ...v, regras: v.regras.map((r) => {
      const cal = r.calendarioId ? this.b.calendarios.find((x) => x.id === r.calendarioId) : null
      return { ...r, calendario: cal ? { id: cal.id, codigo: cal.fusoHorario, nome: cal.nome } : null }
    }) }
  }
  private versoesDe(slaId: string) {
    return this.b.versoes.filter((v) => v.slaId === slaId).sort((a, b) => b.numero - a.numero)
  }
  private vigenteEmData(slaId: string, data: string) {
    return this.versoesDe(slaId).find((v) => (v.situacao === 'VIGENTE' || v.situacao === 'ENCERRADA') && v.vigenciaInicio && data >= v.vigenciaInicio && (!v.vigenciaFim || data <= v.vigenciaFim))
  }
  private slaResumo(s: SlaDb): SlaResumo {
    const c = this.b.contratos.find((x) => x.id === s.contratoId)
    const vs = this.versoesDe(s.id)
    const vig = this.vigenteEmData(s.id, hoje())
    return { id: s.id, codigo: s.codigo, nome: s.nome, tipoResponsavel: s.tipoResponsavel, contrato: c ? { id: c.id, codigo: c.numero, nome: c.objeto } : null,
      contratadaNome: c ? this.b.contratadas.find((k) => k.id === c.contratadaId)!.razaoSocial : null, equipeInterna: s.equipeInterna,
      versaoVigente: vig?.numero ?? null, vigenteDesde: vig?.vigenciaInicio ?? null, situacaoUltimaVersao: vs[0]?.situacao ?? null, situacao: s.situacao }
  }
  private slaDetalhe(s: SlaDb): SlaDetalhe {
    const r = this.slaResumo(s)
    return { ...s, contrato: r.contrato, contratadaNome: r.contratadaNome,
      servicos: s.servicoIds.map((id) => this.ref(this.b.servicos, id)).sort((a, b) => a.codigo.localeCompare(b.codigo)),
      versoes: this.versoesDe(s.id).map((v) => this.versaoSaida(v)) }
  }

  // ------------------------------------------------------------ módulo Configuração
  private modulo(metodo: string, rota: string, q: URLSearchParams, op: Opcoes, u: SessaoDb) {
    const c = (op.corpo ?? {}) as Record<string, any>
    const m = (re: string) => rota.match(new RegExp('^' + re + '$'))
    const ok = (dados: unknown, etag?: number) => ({ status: 200, dados, etag: etag === undefined ? undefined : `"${etag}"` })
    const criado = (dados: unknown, versao = 0) => ({ status: 201, dados, etag: `"${versao}"` })
    const just = texto(c.justificativa)
    let r: RegExpMatchArray | null

    // painel
    if (rota === '/painel') return ok(this.painel(u))
    if (rota === '/usuarios') return ok(USUARIOS_CORE.filter((x) => !q.get('termo') || contem(x.nomeExibicao, q.get('termo')!)))
    if (rota === '/eventos' && metodo === 'POST') return ok(this.consumirEvento(c, u))

    // ---------- cadastros de apoio (N07, N08, N09)
    const apoio: Array<[string, 'unidades' | 'fontes' | 'servicos', string, string]> = [
      ['unidades-medida', 'unidades', 'unidade-medida', 'sigla'], ['fontes-recurso', 'fontes', 'fonte-recurso', 'codigo'], ['servicos', 'servicos', 'servico', 'codigo']]
    for (const [seg, col, ent, chave] of apoio) {
      const lista = this.b[col] as Array<any>
      const saida = (x: any) => (col === 'servicos' ? this.servicoSaida(x) : x)
      if (rota === '/' + seg && metodo === 'GET') {
        const termo = q.get('termo')
        const f = lista.filter((x) => (!q.get('situacao') || x.situacao === q.get('situacao')) && (!q.get('tipo') || x.tipo === q.get('tipo'))
          && (!termo || contem(x[chave], termo) || contem(x.nome, termo)))
        return ok(paginar(f.map(saida), q, { [chave]: (x: any) => x[chave], nome: (x: any) => x.nome, situacao: (x: any) => x.situacao, tipo: (x: any) => x.tipo ?? '' }, chave))
      }
      if (rota === '/' + seg && metodo === 'POST') {
        exigir(u, GESTAO)
        const x: any = { id: uuid(), situacao: 'ATIVO', justificativaSituacao: null, version: 0, createdAt: agoraIso(), updatedAt: null }
        this.preencherApoio(col, x, c)
        lista.push(x)
        this.criacao(u, ent, x.id, this.snapApoio(col, x))
        return criado(saida(x))
      }
      if ((r = m(`/${seg}/(${ID})`))) {
        const x = lista.find((y) => y.id === r![1])
        if (!x) throw naoEncontrado('Registro')
        if (metodo === 'GET') return ok(saida(x), x.version)
        exigir(u, GESTAO)
        this.verificarVersao(x.version, op.ifMatch)
        const antes = this.snapApoio(col, x)
        this.preencherApoio(col, x, c)
        this.tocar(x)
        this.alteracao(u, ent, x.id, antes, this.snapApoio(col, x))
        return ok(saida(x), x.version)
      }
      if ((r = m(`/${seg}/(${ID})/(desativacao|reativacao)`))) {
        const x = lista.find((y) => y.id === r![1])
        if (!x) throw naoEncontrado('Registro')
        const ativar = r[2] === 'reativacao'
        if (!ativar) {
          const emUso = col === 'unidades'
            ? this.b.servicos.filter((s) => s.unidadeMedidaId === x.id && s.situacao === 'ATIVO').length + this.b.contratos.filter((k) => k.situacao === 'ATIVO' && k.itens.some((i) => i.unidadeMedidaId === x.id)).length
            : col === 'fontes' ? this.b.contratos.filter((k) => k.situacao === 'ATIVO' && k.fonteIds.includes(x.id)).length
              : this.b.contratos.filter((k) => k.situacao === 'ATIVO' && k.itens.some((i) => i.servicoId === x.id)).length
          if (emUso > 0 && !just) throw erroCampo('justificativa', `O registro está em uso por ${emUso} cadastro(s) ativo(s). Informe a justificativa da desativação.`)
        }
        this.mudarSituacao(u, ent, x, ativar, just)
        return ok(saida(x))
      }
      if ((r = m(`/${seg}/(${ID})/historico`))) return ok(this.historicoDe(ent, r[1]!))
    }

    // ---------- calendários (N10)
    if (rota === '/calendarios' && metodo === 'GET') {
      const t = q.get('termo')
      const f = this.b.calendarios.filter((x) => (!q.get('situacao') || x.situacao === q.get('situacao')) && (!t || contem(x.nome, t)))
      return ok(paginar(f, q, { nome: (x) => x.nome, situacao: (x) => x.situacao, vigenciaInicio: (x) => x.vigenciaInicio }, 'nome'))
    }
    if (rota === '/calendarios' && metodo === 'POST') {
      const x: Calendario = { id: uuid(), situacao: 'ATIVO', justificativaSituacao: null, version: 0, createdAt: agoraIso(), updatedAt: null } as Calendario
      this.preencherCalendario(x, c, true)
      this.b.calendarios.push(x)
      this.criacao(u, 'calendario', x.id, this.snapCal(x))
      return criado(x)
    }
    if ((r = m(`/calendarios/(${ID})`))) {
      const x = this.b.calendarios.find((y) => y.id === r![1])
      if (!x) throw naoEncontrado('Calendário')
      if (metodo === 'GET') return ok(x, x.version)
      this.verificarVersao(x.version, op.ifMatch)
      const antes = this.snapCal(x)
      this.preencherCalendario(x, c, false)
      this.tocar(x)
      this.alteracao(u, 'calendario', x.id, antes, this.snapCal(x))
      return ok(x, x.version)
    }
    if ((r = m(`/calendarios/(${ID})/(desativacao|reativacao)`))) {
      const x = this.b.calendarios.find((y) => y.id === r![1])
      if (!x) throw naoEncontrado('Calendário')
      const emUso = this.b.contratos.filter((k) => k.calendarioId === x.id && k.situacao === 'ATIVO').length
      if (r[2] === 'desativacao' && emUso > 0 && !just) throw erroCampo('justificativa', `O calendário está em uso por ${emUso} contrato(s) ativo(s). Informe a justificativa da desativação.`)
      this.mudarSituacao(u, 'calendario', x, r[2] === 'reativacao', just)
      return ok(x)
    }
    if ((r = m(`/calendarios/(${ID})/simulacoes`))) {
      const x = this.b.calendarios.find((y) => y.id === r![1])
      if (!x) throw naoEncontrado('Calendário')
      const calc = new Calculadora(x.fusoHorario, x.expedientes, x.feriados)
      const inicio = Date.parse(c.inicio)
      const p = regra('meta', () => {
        validarRegra(c.formaContagem, c.unidade, Number(c.meta), true)
        return calcularPrazo(inicio, c.formaContagem, c.unidade, Number(c.meta), calc)
      })
      const util = c.formaContagem === 'CORRIDO_24X7' ? p - inicio : calc.tempoUtil(inicio, p)
      return ok({ inicio: c.inicio, prazo: new Date(p).toISOString(), minutosUteis: Math.round(util / 60000), fusoHorario: x.fusoHorario })
    }
    if ((r = m(`/calendarios/(${ID})/historico`))) return ok(this.historicoDe('calendario', r[1]!))

    // ---------- contratadas (N02)
    if (rota === '/contratadas/validacao-cnpj') {
      const v = q.get('valor') ?? ''
      const erro = validarCnpj(v)
      const n = normalizarCnpj(v)
      return ok({ entrada: v, normalizado: n, formatado: erro ? null : formatarCnpj(n), valido: !erro, alfanumerico: !erro && !/^\d+$/.test(n), mensagem: erro })
    }
    if (rota === '/contratadas' && metodo === 'GET') {
      const t = q.get('termo')
      const fab = this.restrito(u) ? u.contratadaId : null
      const f = this.b.contratadas.filter((x) => (!fab || x.id === fab) && (!q.get('situacao') || x.situacao === q.get('situacao'))
        && (!t || contem(x.razaoSocial, t) || contem(x.nomeFantasia, t) || x.cnpj.includes(normalizarCnpj(t))))
      return ok(paginar(f.map((x) => this.contratadaSaida(x)), q, { razaoSocial: (x) => x.razaoSocial, cnpj: (x) => x.cnpj, situacao: (x) => x.situacao }, 'razaoSocial'))
    }
    if (rota === '/contratadas' && metodo === 'POST') {
      const x = { id: uuid(), situacao: 'ATIVO', justificativaSituacao: null, version: 0, createdAt: agoraIso(), updatedAt: null } as Banco['contratadas'][number]
      this.preencherContratada(x, c)
      this.b.contratadas.push(x)
      this.criacao(u, 'contratada', x.id, { cnpj: formatarCnpj(x.cnpj), razaoSocial: x.razaoSocial, nomeFantasia: x.nomeFantasia })
      return criado(this.contratadaSaida(x))
    }
    if ((r = m(`/contratadas/(${ID})(/.*)?`))) {
      const x = this.b.contratadas.find((y) => y.id === r![1])
      if (!x || (this.restrito(u) && u.contratadaId !== x.id)) throw naoEncontrado('Contratada')
      const sub = r[2] ?? ''
      if (sub === '' && metodo === 'GET') return ok(this.contratadaSaida(x), x.version)
      if (sub === '' && metodo === 'PUT') {
        this.verificarVersao(x.version, op.ifMatch)
        const snap = (y: typeof x) => ({ cnpj: formatarCnpj(y.cnpj), razaoSocial: y.razaoSocial, nomeFantasia: y.nomeFantasia })
        const antes = snap(x)
        this.preencherContratada(x, c)
        this.tocar(x)
        this.alteracao(u, 'contratada', x.id, antes, snap(x))
        return ok(this.contratadaSaida(x), x.version)
      }
      if (sub === '/desativacao' || sub === '/reativacao') {
        const vig = this.b.contratos.filter((k) => k.contratadaId === x.id && this.utilizavel(k)).length
        if (sub === '/desativacao' && vig > 0 && !just) throw erroCampo('justificativa', `A contratada tem ${vig} contrato(s) vigente(s). Informe a justificativa da desativação.`)
        this.mudarSituacao(u, 'contratada', x, sub === '/reativacao', just)
        return ok(this.contratadaSaida(x))
      }
      if (sub === '/historico') return ok(this.historicoDe('contratada', x.id))
      if (sub === '/equipe' && metodo === 'GET') {
        return ok(this.b.membros.filter((y) => y.contratadaId === x.id).sort((a, b) => b.inicio.localeCompare(a.inicio))
          .map(({ contratadaId: _k, ...y }) => ({ ...y, vigente: vigenteEm(hoje(), y.inicio, y.fim) })))
      }
      if (sub === '/equipe' && metodo === 'POST') {
        if (x.situacao !== 'ATIVO') throw erroCampo('contratadaId', 'A contratada está desativada.')
        const uc = USUARIOS_CORE.find((y) => y.id === c.usuarioId)
        if (!uc) throw erroCampo('usuarioId', 'Usuário não encontrado no Core.')
        if (!c.inicio) throw erroCampo('inicio', 'Campo obrigatório.')
        if (c.fim && c.fim < c.inicio) throw erroCampo('fim', 'O fim do período não pode ser anterior ao início.')
        const sobre = this.b.membros.some((y) => y.usuarioId === uc.id && !(y.fim && y.fim < c.inicio) && !(c.fim && c.fim < y.inicio))
        if (sobre) throw conflito('usuarioId', 'O usuário já faz parte de uma equipe de contratada nesse período.')
        const mb = { id: uuid(), contratadaId: x.id, usuarioId: uc.id, nomeExibicao: uc.nomeExibicao, papel: c.papel, inicio: c.inicio, fim: texto(c.fim), vigente: true, mfaObrigatorio: true, version: 0 }
        this.b.membros.push(mb)
        this.hist(u, 'contratada', x.id, 'EQUIPE_INCLUSAO', null, null, `${mb.nomeExibicao} como ${mb.papel} a partir de ${mb.inicio}`)
        return criado(mb)
      }
      if ((r = sub.match(new RegExp(`^/equipe/(${ID})/encerramento$`)))) {
        const mb = this.b.membros.find((y) => y.id === r![1] && y.contratadaId === x.id)
        if (!mb) throw naoEncontrado('Membro')
        if (!c.fim || c.fim < mb.inicio) throw erroCampo('fim', `O fim do período não pode ser anterior ao início (${mb.inicio}).`)
        this.hist(u, 'contratada-membro', mb.id, 'ALTERACAO', 'fim', mb.fim, c.fim)
        mb.fim = c.fim
        mb.version++
        this.hist(u, 'contratada', x.id, 'EQUIPE_ENCERRAMENTO', null, null, `${mb.nomeExibicao} até ${c.fim}`)
        return ok(mb)
      }
      if (sub === '/alteracoes' && metodo === 'GET') {
        return ok(this.b.alteracoes.filter((a) => a.contratadaId === x.id).sort((a, b) => b.dataAlteracao.localeCompare(a.dataAlteracao)).map(({ anexoBase64: _a, contratadaId: _c, ...a }) => a))
      }
      if (sub === '/alteracoes' && metodo === 'POST') {
        const fd = op.formData
        const dados = fd ? { dataAlteracao: fd.get('dataAlteracao') as string, tipo: fd.get('tipo') as string, descricao: fd.get('descricao') as string } : c
        if (!dados.dataAlteracao) throw erroCampo('dataAlteracao', 'Campo obrigatório.')
        if (dados.dataAlteracao > hoje()) throw erroCampo('dataAlteracao', 'A data da alteração não pode ser futura.')
        if (!texto(dados.tipo)) throw erroCampo('tipo', 'Campo obrigatório.')
        if (!texto(dados.descricao)) throw erroCampo('descricao', 'Campo obrigatório.')
        const arq = fd?.get('anexo') as File | null
        if (arq && arq.size > 0) {
          if (!['application/pdf', 'image/png', 'image/jpeg'].includes(arq.type)) throw erroCampo('anexo', 'Tipo de arquivo não permitido. Envie PDF, PNG ou JPEG.')
          if (arq.size > 5 * 1024 * 1024) throw erroCampo('anexo', 'O anexo deve ter no máximo 5 MB.')
        }
        const a = { id: uuid(), contratadaId: x.id, dataAlteracao: dados.dataAlteracao, tipo: dados.tipo.trim(), descricao: dados.descricao.trim(),
          anexoNome: arq && arq.size ? arq.name : null, anexoTipo: arq && arq.size ? arq.type : null, anexoTamanho: arq && arq.size ? arq.size : null,
          createdAt: agoraIso(), createdByNome: u.nome, anexoBase64: null }
        this.b.alteracoes.push(a)
        this.hist(u, 'contratada', x.id, 'ALTERACAO_CONTRATUAL', null, null, `${a.tipo} em ${a.dataAlteracao}${a.anexoNome ? ` (anexo ${a.anexoNome})` : ''}`, a.descricao)
        const { anexoBase64: _b, contratadaId: _k, ...saida } = a
        return criado(saida)
      }
      if ((r = sub.match(new RegExp(`^/alteracoes/(${ID})/anexo$`)))) {
        const a = this.b.alteracoes.find((y) => y.id === r![1])
        if (!a?.anexoNome) throw naoEncontrado('Anexo')
        return { status: 200, blob: new Blob([`Anexo de demonstração: ${a.anexoNome}. No modo demonstração o conteúdo não é armazenado.`], { type: 'text/plain' }) }
      }
    }

    // ---------- contratos (N01)
    if (rota === '/contratos' && metodo === 'GET') {
      const t = q.get('termo')
      const fab = this.restrito(u) ? u.contratadaId : q.get('contratadaId')
      const vig = q.get('vigenteEm')
      const f = this.b.contratos.filter((x) => (!q.get('situacao') || x.situacao === q.get('situacao')) && (!fab || x.contratadaId === fab)
        && (!vig || (x.vigenciaInicio <= vig && x.vigenciaFim >= vig)) && (!t || contem(x.numero, t) || contem(x.objeto, t)))
      const nomes = (x: ContratoDb) => this.b.contratadas.find((k) => k.id === x.contratadaId)!.razaoSocial
      const ordenados = paginar(f, q, { numero: (x) => x.numero, vigenciaInicio: (x) => x.vigenciaInicio, vigenciaFim: (x) => x.vigenciaFim, situacao: (x) => x.situacao, contratada: nomes }, 'numero')
      return ok({ ...ordenados, content: ordenados.content.map((x) => this.contratoResumo(x)) })
    }
    if (rota === '/contratos' && metodo === 'POST') {
      const chave = op.chaveIdempotencia ? `${u.id}:POST /contratos:${op.chaveIdempotencia}` : null
      if (chave && this.b.idempotencia[chave]) {
        const ex = this.b.contratos.find((x) => x.id === this.b.idempotencia[chave])!
        return { status: 200, dados: this.contratoDetalhe(ex), etag: `"${ex.version}"`, cabecalhos: { 'idempotent-replayed': 'true' } }
      }
      const x = { id: uuid(), situacao: 'ATIVO', justificativaSituacao: null, version: 0, createdAt: agoraIso(), updatedAt: null, sistemas: [] } as unknown as ContratoDb
      this.preencherContrato(x, c)
      const fiscais = (c.fiscais ?? []) as Array<Record<string, string>>
      if (!fiscais.length) throw erroCampo('fiscais', 'Designe ao menos um fiscal para o contrato (comissão de fiscalização).')
      const novos = fiscais.map((f) => this.novoFiscal(x.id, f.usuarioId!, f.tipoFiscal as FiscalDb['tipoFiscal'], f.inicio!, texto(f.fim)))
      const ref = x.vigenciaInicio > hoje() ? x.vigenciaInicio : hoje()
      if (!novos.some((f) => vigenteEm(ref, f.inicio, f.fim))) throw erroCampo('fiscais', 'Ao menos um fiscal deve ter designação vigente no início do contrato.')
      this.b.contratos.push(x)
      this.b.fiscais.push(...novos)
      this.criacao(u, 'contrato', x.id, this.snapContrato(x))
      novos.forEach((f) => this.hist(u, 'contrato', x.id, 'FISCAL_DESIGNACAO', null, null, `${f.nomeExibicao} como ${f.tipoFiscal} de ${f.inicio}`))
      if (chave) this.b.idempotencia[chave] = x.id
      return criado(this.contratoDetalhe(x))
    }
    if ((r = m(`/contratos/(${ID})(/.*)?`))) {
      const x = this.b.contratos.find((y) => y.id === r![1])
      if (!x || (this.restrito(u) && u.contratadaId !== x.contratadaId)) throw naoEncontrado('Contrato')
      const sub = r[2] ?? ''
      if (sub === '' && metodo === 'GET') return ok(this.contratoDetalhe(x), x.version)
      if (sub === '' && metodo === 'PUT') {
        this.verificarVersao(x.version, op.ifMatch)
        if (x.situacao !== 'ATIVO') throw erroCampo('situacao', 'Reative o contrato antes de alterá-lo.')
        const antes = this.snapContrato(x)
        const copia = structuredClone(x)
        this.preencherContrato(copia, c)
        Object.assign(x, copia)
        this.tocar(x)
        this.alteracao(u, 'contrato', x.id, antes, this.snapContrato(x))
        return ok(this.contratoDetalhe(x), x.version)
      }
      if (sub === '/desativacao' || sub === '/reativacao') {
        if (!just) throw erroCampo('justificativa', 'Informe a justificativa.')
        this.mudarSituacao(u, 'contrato', x, sub === '/reativacao', just)
        return ok(this.contratoDetalhe(x))
      }
      if (sub === '/historico') return ok(this.historicoDe('contrato', x.id))
      if (sub === '/aditivos') {
        const chave = op.chaveIdempotencia ? `${u.id}:aditivo:${x.id}:${op.chaveIdempotencia}` : null
        if (chave && this.b.idempotencia[chave]) return { status: 200, dados: this.contratoDetalhe(x), cabecalhos: { 'idempotent-replayed': 'true' } }
        this.registrarAditivo(u, x, c)
        if (chave) this.b.idempotencia[chave] = x.id
        return ok(this.contratoDetalhe(x), x.version)
      }
      if (sub === '/fiscais') {
        const f = this.novoFiscal(x.id, c.usuarioId, c.tipoFiscal, c.inicio, texto(c.fim))
        this.b.fiscais.push(f)
        this.hist(u, 'contrato', x.id, 'FISCAL_DESIGNACAO', null, null, `${f.nomeExibicao} como ${f.tipoFiscal} de ${f.inicio}`)
        return ok(this.contratoDetalhe(x))
      }
      if ((r = sub.match(new RegExp(`^/fiscais/(${ID})/(substituicao|encerramento)$`)))) {
        const f = this.b.fiscais.find((y) => y.id === r![1] && y.contratoId === x.id)
        if (!f) throw naoEncontrado('Designação de fiscal')
        const motivo = texto(c.motivo)
        if (!motivo) throw erroCampo('motivo', 'Campo obrigatório.')
        if (r[2] === 'substituicao') {
          if (f.fim && f.fim < hoje()) throw erroCampo('fiscalId', 'A designação já está encerrada.')
          if (!c.inicio || c.inicio <= f.inicio) throw erroCampo('inicio', 'A nova designação deve começar depois do início da designação substituída.')
          const novo = this.novoFiscal(x.id, c.usuarioId, f.tipoFiscal, c.inicio, f.fim)
          this.hist(u, 'contrato-fiscal', f.id, 'ALTERACAO', 'fim', f.fim, somarDias(c.inicio, -1), motivo)
          f.fim = somarDias(c.inicio, -1)
          f.motivoEncerramento = 'Substituição: ' + motivo
          f.substituidoPorId = novo.id
          this.b.fiscais.push(novo)
          this.hist(u, 'contrato', x.id, 'FISCAL_SUBSTITUICAO', null, null, `${f.nomeExibicao} -> ${novo.nomeExibicao} (${novo.tipoFiscal}) a partir de ${novo.inicio}`, motivo)
        } else {
          if (!c.fim || c.fim < f.inicio) throw erroCampo('fim', 'O fim da designação não pode ser anterior ao início.')
          const refData = somarDias(c.fim, 1) > hoje() ? somarDias(c.fim, 1) : hoje()
          const resta = this.fiscaisDe(x.id).some((y) => y.id !== f.id && vigenteEm(refData, y.inicio, y.fim))
          if (x.situacao === 'ATIVO' && !resta && this.utilizavel(x, refData)) {
            throw new Erro(422, 'contrato-sem-fiscal', 'Contrato ficaria sem fiscal', 'Cada contrato deve ter ao menos um fiscal com designação vigente. Use a substituição.', 'fim')
          }
          this.hist(u, 'contrato-fiscal', f.id, 'ALTERACAO', 'fim', f.fim, c.fim, motivo)
          f.fim = c.fim
          f.motivoEncerramento = motivo
          this.hist(u, 'contrato', x.id, 'FISCAL_ENCERRAMENTO', null, null, `${f.nomeExibicao} até ${c.fim}`, motivo)
        }
        return ok(this.contratoDetalhe(x))
      }
      if (sub === '/sistemas' && metodo === 'POST') {
        exigir(u, GESTAO)
        const s = SISTEMAS.find((y) => y.id === c.sistemaId)
        if (!s) throw erroCampo('sistemaId', 'Sistema não encontrado no Portfólio.')
        if (!x.sistemas.some((y) => y.sistemaId === s.id)) {
          x.sistemas.push({ sistemaId: s.id, sistemaNome: s.nome })
          this.hist(u, 'contrato', x.id, 'SISTEMA_VINCULADO', null, null, `${s.nome} (${s.sigla})`)
        }
        return ok(this.contratoDetalhe(x))
      }
      if ((r = sub.match(new RegExp(`^/sistemas/(${ID})$`))) && metodo === 'DELETE') {
        const i = x.sistemas.findIndex((y) => y.sistemaId === r![1])
        if (i >= 0) {
          this.hist(u, 'contrato', x.id, 'SISTEMA_DESVINCULADO', null, null, x.sistemas[i]!.sistemaNome)
          x.sistemas.splice(i, 1)
        }
        return ok(this.contratoDetalhe(x))
      }
    }

    // ---------- SLA (N03)
    if (rota === '/slas' && metodo === 'GET') {
      const t = q.get('termo')
      const fab = this.restrito(u) ? u.contratadaId : null
      const f = this.b.slas.filter((x) => (!fab || x.contratadaId === fab) && (!q.get('situacao') || x.situacao === q.get('situacao'))
        && (!q.get('tipo') || x.tipoResponsavel === q.get('tipo')) && (!q.get('contratoId') || x.contratoId === q.get('contratoId'))
        && (!t || contem(x.codigo, t) || contem(x.nome, t)))
      const p = paginar(f, q, { codigo: (x) => x.codigo, nome: (x) => x.nome, situacao: (x) => x.situacao }, 'codigo')
      return ok({ ...p, content: p.content.map((x) => this.slaResumo(x)) })
    }
    if (rota === '/slas' && metodo === 'POST') {
      const x = { id: uuid(), situacao: 'ATIVO', justificativaSituacao: null, version: 0, createdAt: agoraIso(), updatedAt: null, sistemas: [], servicoIds: [] } as unknown as SlaDb
      this.preencherSla(x, c, true)
      const v: VersaoDb = this.novaVersaoDb(x.id, 1, 'Versão inicial')
      v.regras = this.regrasDe(c.regras ?? [])
      this.b.slas.push(x)
      this.b.versoes.push(v)
      this.criacao(u, 'sla', x.id, { codigo: x.codigo, nome: x.nome, tipoResponsavel: x.tipoResponsavel })
      return criado(this.slaDetalhe(x))
    }
    if ((r = m(`/slas/(${ID})(/.*)?`))) {
      const x = this.b.slas.find((y) => y.id === r![1])
      if (!x || (this.restrito(u) && x.contratadaId !== u.contratadaId)) throw naoEncontrado('SLA')
      const sub = r[2] ?? ''
      if (sub === '' && metodo === 'GET') return ok(this.slaDetalhe(x), x.version)
      if (sub === '' && metodo === 'PUT') {
        this.verificarVersao(x.version, op.ifMatch)
        if (c.tipoResponsavel !== x.tipoResponsavel) throw erroCampo('tipoResponsavel', 'O responsável do SLA não pode ser alterado. Crie outro SLA.')
        const snap = (y: SlaDb) => ({ codigo: y.codigo, nome: y.nome, equipeInterna: y.equipeInterna, servicos: y.servicoIds.length, sistemas: y.sistemas.map((s) => s.sistemaNome).join(', ') })
        const antes = snap(x)
        const copia = structuredClone(x)
        this.preencherSla(copia, c, false)
        Object.assign(x, copia)
        this.tocar(x)
        this.alteracao(u, 'sla', x.id, antes, snap(x))
        return ok(this.slaDetalhe(x), x.version)
      }
      if (sub === '/desativacao' || sub === '/reativacao') {
        if (sub === '/desativacao' && this.vigenteEmData(x.id, hoje()) && !just) throw erroCampo('justificativa', 'O SLA tem versão vigente. Informe a justificativa da desativação.')
        this.mudarSituacao(u, 'sla', x, sub === '/reativacao', just)
        return ok(this.slaDetalhe(x))
      }
      if (sub === '/historico') return ok(this.historicoDe('sla', x.id))
      if (sub === '/versao-vigente') {
        const d = q.get('data') ?? hoje()
        const v = this.vigenteEmData(x.id, d)
        return ok({ slaId: x.id, codigo: x.codigo, data: d, versao: v ? this.versaoSaida(v) : null })
      }
      if (sub === '/versoes' && metodo === 'POST') {
        const vs = this.versoesDe(x.id)
        if (vs.some((v) => v.situacao === 'RASCUNHO' || v.situacao === 'EM_AVALIACAO')) throw new Erro(409, 'versao-aberta', 'Já existe versão em elaboração', 'Conclua ou devolva a versão em rascunho/avaliação antes de criar outra.')
        if (!texto(c.motivoAlteracao)) throw erroCampo('motivoAlteracao', 'Campo obrigatório.')
        const v = this.novaVersaoDb(x.id, (vs[0]?.numero ?? 0) + 1, c.motivoAlteracao.trim())
        v.regras = (vs[0]?.regras ?? []).map((rg) => ({ ...rg, id: uuid() }))
        this.b.versoes.push(v)
        this.hist(u, 'sla', x.id, 'SLA_NOVA_VERSAO', null, null, `Versão ${v.numero} criada em rascunho`, v.motivoAlteracao)
        return ok(this.versaoSaida(v))
      }
      if (sub === '/simulacoes') {
        const rg = this.versoesDe(x.id).flatMap((v) => v.regras).find((y) => y.id === c.regraId)
        if (!rg) throw naoEncontrado('Regra')
        if (rg.metaUnidade === 'PERCENTUAL') throw erroCampo('regraId', 'Indicadores percentuais não têm prazo para simular.')
        const pausas: Pausa[] = (c.pausas ?? []).map((p: any) => ({ inicio: Date.parse(p.inicio), fim: p.fim ? Date.parse(p.fim) : null, motivo: p.motivo }))
        if (pausas.length && !rg.permitePausa) throw erroCampo('pausas', 'Esta regra não permite pausa.')
        const agora = c.agora ? Date.parse(c.agora) : Date.now()
        const a = regra('regraId', () => avaliar(Date.parse(c.inicio), agora, pausas, rg.formaContagem, rg.metaUnidade, rg.metaValor, this.calc(rg.calendarioId), c.limiarPercentual ?? 80))
        return ok({ inicio: c.inicio, agora: new Date(agora).toISOString(), prazo: new Date(a.prazo).toISOString(), minutosConsumidos: Math.round(a.consumido / 60000),
          minutosMeta: Math.round(a.meta / 60000), percentualConsumido: a.percentual, situacao: a.situacao })
      }
      if (sub === '/sistemas' && metodo === 'POST') {
        const s = SISTEMAS.find((y) => y.id === c.sistemaId)
        if (!s) throw erroCampo('sistemaId', 'Sistema não encontrado no Portfólio.')
        if (!x.sistemas.some((y) => y.sistemaId === s.id)) {
          x.sistemas.push({ sistemaId: s.id, sistemaNome: s.nome })
          this.hist(u, 'sla', x.id, 'SISTEMA_VINCULADO', null, null, `${s.nome} (${s.sigla})`)
        }
        return ok(this.slaDetalhe(x))
      }
      if ((r = sub.match(new RegExp(`^/sistemas/(${ID})$`))) && metodo === 'DELETE') {
        const i = x.sistemas.findIndex((y) => y.sistemaId === r![1])
        if (i >= 0) {
          this.hist(u, 'sla', x.id, 'SISTEMA_DESVINCULADO', null, null, x.sistemas[i]!.sistemaNome)
          x.sistemas.splice(i, 1)
        }
        return ok(this.slaDetalhe(x))
      }
      if ((r = sub.match(new RegExp(`^/versoes/(${ID})/(regras|envio-avaliacao|parecer|decisao)$`)))) {
        const v = this.b.versoes.find((y) => y.id === r![1] && y.slaId === x.id)
        if (!v) throw naoEncontrado('Versão do SLA')
        return ok(this.fluxoSla(u, x, v, r[2]!, c, op), v.version)
      }
    }

    // ---------- notificações (N06)
    if (rota === '/notificacoes/eventos') return ok(CATALOGO)
    if (rota === '/notificacoes/pre-visualizacao') {
      const ev = CATALOGO.find((e) => e.evento === c.evento)
      if (!ev) throw erroCampo('evento', `Evento ${c.evento} não está no catálogo de notificações.`)
      const ex: Record<string, string> = {}
      ev.variaveis.forEach((v) => (ex[v] = EXEMPLOS[v] ?? `[${v}]`))
      const render = (s: string) => s.replace(/\{\{\s*([a-zA-Z][a-zA-Z0-9_.]*)\s*}}/g, (_, n) => ex[n] ?? '')
      return ok({ assunto: render(c.assunto ?? ''), mensagem: render(c.modelo ?? ''), exemplo: ex })
    }
    if (rota === '/notificacoes' && metodo === 'GET') {
      const t = q.get('termo')
      const f = this.b.notificacoes.filter((x) => (!q.get('situacao') || x.situacao === q.get('situacao')) && (!q.get('evento') || x.evento === q.get('evento'))
        && (!t || contem(x.nome, t) || contem(x.evento, t)))
      return ok(paginar(f, q, { nome: (x) => x.nome, evento: (x) => x.evento, situacao: (x) => x.situacao }, 'nome'))
    }
    if (rota === '/notificacoes' && metodo === 'POST') {
      const x = { id: uuid(), situacao: 'ATIVO', justificativaSituacao: null, version: 0, createdAt: agoraIso(), updatedAt: null } as Notificacao
      this.preencherNotificacao(x, c)
      this.b.notificacoes.push(x)
      this.criacao(u, 'notificacao', x.id, { nome: x.nome, evento: x.evento, perfis: x.perfis.join(',') })
      return criado(x)
    }
    if ((r = m(`/notificacoes/(${ID})(/.*)?`))) {
      const x = this.b.notificacoes.find((y) => y.id === r![1])
      if (!x) throw naoEncontrado('Notificação')
      const sub = r[2] ?? ''
      if (sub === '' && metodo === 'GET') return ok(x, x.version)
      if (sub === '' && metodo === 'PUT') {
        this.verificarVersao(x.version, op.ifMatch)
        const snap = (y: Notificacao) => ({ nome: y.nome, evento: y.evento, canais: `${y.canalApp ? 'Ferramenta ' : ''}${y.canalEmail ? 'E-mail' : ''}`, perfis: y.perfis.join(','), assunto: y.assunto, modelo: y.modelo, limiarPercentual: y.limiarPercentual })
        const antes = snap(x)
        this.preencherNotificacao(x, c)
        this.tocar(x)
        this.alteracao(u, 'notificacao', x.id, antes, snap(x))
        return ok(x, x.version)
      }
      if (sub === '/desativacao' || sub === '/reativacao') {
        this.mudarSituacao(u, 'notificacao', x, sub === '/reativacao', just)
        return ok(x)
      }
      if (sub === '/historico') return ok(this.historicoDe('notificacao', x.id))
    }

    // ---------- portfólio (N04) e governança (N05)
    if (rota === '/portfolio/sistemas') {
      const t = q.get('termo')
      const h = hoje()
      const lista = SISTEMAS.filter((s) => !t || contem(s.nome, t) || contem(s.sigla, t)).map((s) => {
        const cs = this.b.contratos.filter((k) => k.sistemas.some((y) => y.sistemaId === s.id))
        const ss = this.b.slas.filter((k) => k.sistemas.some((y) => y.sistemaId === s.id))
        const vig = ss.some((k) => k.situacao === 'ATIVO' && this.vigenteEmData(k.id, h))
        return { ...s, contratos: cs.map((k) => ({ id: k.id, codigo: k.numero, nome: k.objeto })), slas: ss.map((k) => ({ id: k.id, codigo: k.codigo, nome: k.nome })),
          temSlaVigente: vig, pendenciaConfiguracao: s.situacao === 'ATIVO' && !vig }
      }).filter((s) => q.get('somentePendentes') !== 'true' || s.pendenciaConfiguracao)
      return ok({ portfolioDisponivel: true, sistemas: lista })
    }
    if (rota === '/auditoria/governanca') {
      const de = q.get('de'), ate = q.get('ate')
      const f = this.b.historico.filter((h) => (!q.get('usuarioId') || h.usuarioId === q.get('usuarioId')) && (!q.get('entidade') || h.entidade === q.get('entidade'))
        && (!q.get('entidadeId') || h.entidadeId === q.get('entidadeId')) && (!q.get('acao') || h.acao === q.get('acao'))
        && (!de || h.ocorridoEm >= de) && (!ate || h.ocorridoEm < ate))
      return ok(paginar(f, q, { ocorridoEm: (x) => x.ocorridoEm, entidade: (x) => x.entidade, usuarioNome: (x) => x.usuarioNome, acao: (x) => x.acao }, 'ocorridoEm,desc'))
    }
    throw new Erro(404, 'rota-inexistente', 'Rota inexistente', 'A rota solicitada não existe.')
  }

  // ------------------------------------------------------------ fluxo da versão do SLA (RF17, RN14)
  private fluxoSla(u: SessaoDb, s: SlaDb, v: VersaoDb, acao: string, c: Record<string, any>, op: Opcoes) {
    if (acao === 'regras') {
      exigir(u, GESTAO)
      this.verificarVersao(v.version, op.ifMatch)
      if (v.situacao !== 'RASCUNHO') throw erroCampo('situacao', 'Somente versões em rascunho podem ser editadas. Crie uma nova versão.')
      const antes = v.regras.map((r) => r.classe + '/' + r.indicador + '=' + r.metaValor).join('; ')
      v.regras = this.regrasDe(c.regras ?? [])
      v.version++
      this.hist(u, 'sla-versao', v.id, 'ALTERACAO', 'regras', antes, v.regras.map((r) => r.classe + '/' + r.indicador + '=' + r.metaValor).join('; '))
    } else if (acao === 'envio-avaliacao') {
      exigir(u, GESTAO)
      if (v.situacao !== 'RASCUNHO') throw erroCampo('situacao', `A versão está ${v.situacao.toLowerCase()} e não pode ser enviada para avaliação.`)
      if (!v.regras.length) throw erroCampo('regras', 'Cadastre ao menos uma regra antes de enviar para avaliação.')
      Object.assign(v, { situacao: 'EM_AVALIACAO', parecerFavoravel: null, parecerTexto: null, parecerPorNome: null, parecerEm: null })
      v.version++
      this.hist(u, 'sla', s.id, 'SLA_EM_AVALIACAO', null, null, `Versão ${v.numero} enviada para avaliação`)
    } else if (acao === 'parecer') {
      if (s.tipoResponsavel !== 'CONTRATADA' || s.contratadaId !== u.contratadaId) throw proibido('O parecer só pode ser registrado pelo gestor da fábrica do contrato.')
      if (v.situacao !== 'EM_AVALIACAO') throw erroCampo('situacao', 'A versão não está em avaliação.')
      if (typeof c.favoravel !== 'boolean') throw erroCampo('favoravel', 'Campo obrigatório.')
      if (!texto(c.texto)) throw erroCampo('texto', 'Campo obrigatório.')
      Object.assign(v, { parecerFavoravel: c.favoravel, parecerTexto: c.texto.trim(), parecerPorNome: u.nome, parecerEm: agoraIso() })
      v.version++
      this.hist(u, 'sla', s.id, 'SLA_PARECER', null, null, `Versão ${v.numero}: parecer ${c.favoravel ? 'favorável' : 'desfavorável'}`, c.texto.trim())
    } else {
      exigir(u, GESTAO)
      if (v.situacao !== 'EM_AVALIACAO') throw erroCampo('situacao', 'A versão não está em avaliação.')
      if (!c.aprovar) {
        if (!texto(c.observacao)) throw erroCampo('observacao', 'Informe o motivo da devolução.')
        Object.assign(v, { situacao: 'RASCUNHO', decisaoObservacao: c.observacao.trim(), decisaoPorNome: u.nome, decisaoEm: agoraIso() })
        v.version++
        this.hist(u, 'sla', s.id, 'SLA_DEVOLVIDO', null, null, `Versão ${v.numero} devolvida para rascunho`, c.observacao.trim())
        return this.versaoSaida(v)
      }
      if (s.situacao !== 'ATIVO') throw erroCampo('situacao', 'Reative o SLA antes de tornar uma versão vigente.')
      if (s.tipoResponsavel === 'CONTRATADA' && v.parecerFavoravel === null) throw erroCampo('parecer', 'Aguarde o parecer do gestor da fábrica antes de decidir a vigência.')
      const inicio = c.vigenciaInicio as string | undefined
      const anterior = this.versoesDe(s.id).find((y) => y.situacao === 'VIGENTE')
      if (!inicio) throw erroCampo('vigenciaInicio', 'Informe o início da vigência da versão.')
      if (inicio < hoje()) throw erroCampo('vigenciaInicio', 'A vigência não pode ser retroativa: informe hoje ou uma data futura.')
      if (anterior?.vigenciaInicio && inicio <= anterior.vigenciaInicio) throw erroCampo('vigenciaInicio', 'A nova vigência deve começar depois do início da versão vigente.')
      const contrato = this.b.contratos.find((k) => k.id === s.contratoId)
      if (contrato && !this.utilizavel(contrato, inicio)) throw erroCampo('vigenciaInicio', `O contrato ${contrato.numero} não está vigente nessa data (RN01).`)
      Object.assign(v, { situacao: 'VIGENTE', vigenciaInicio: inicio, decisaoObservacao: texto(c.observacao), decisaoPorNome: u.nome, decisaoEm: agoraIso() })
      v.version++
      if (anterior) {
        anterior.vigenciaFim = somarDias(inicio, -1)
        if (inicio <= hoje()) anterior.situacao = 'ENCERRADA'
      }
      this.hist(u, 'sla', s.id, 'SLA_VIGENCIA', null, null, `Versão ${v.numero} vigente a partir de ${inicio}${anterior ? `; versão ${anterior.numero} até ${anterior.vigenciaFim}` : ''}`, texto(c.observacao))
    }
    return this.versaoSaida(v)
  }

  // ------------------------------------------------------------ preenchimentos com validação
  private preencherApoio(col: string, x: any, c: Record<string, any>) {
    const obrig = (k: string) => {
      if (!texto(c[k])) throw erroCampo(k, 'Campo obrigatório.')
      return c[k].trim()
    }
    if (col === 'unidades') {
      const sigla = obrig('sigla').toUpperCase()
      if (this.b.unidades.some((y) => y.id !== x.id && y.sigla === sigla)) throw conflito('sigla', `Já existe uma unidade de medida com a sigla ${sigla}.`)
      Object.assign(x, { sigla, nome: obrig('nome'), descricao: texto(c.descricao) })
    } else {
      const codigo = obrig('codigo').toUpperCase()
      const lista = (col === 'fontes' ? this.b.fontes : this.b.servicos) as Array<{ id: string; codigo: string }>
      if (lista.some((y) => y.id !== x.id && y.codigo === codigo)) throw conflito('codigo', `Já existe um registro com o código ${codigo}.`)
      Object.assign(x, { codigo, nome: obrig('nome'), descricao: texto(c.descricao) })
      if (col === 'servicos') {
        if (!c.tipo) throw erroCampo('tipo', 'Campo obrigatório.')
        const um = this.b.unidades.find((y) => y.id === c.unidadeMedidaId)
        if (!um) throw erroCampo('unidadeMedidaId', 'Campo obrigatório.')
        if (um.situacao !== 'ATIVO' && x.unidadeMedidaId !== um.id) throw erroCampo('unidadeMedidaId', `A unidade de medida ${um.sigla} está desativada.`)
        Object.assign(x, { tipo: c.tipo, unidadeMedidaId: um.id })
      }
    }
  }
  private snapApoio(col: string, x: any) {
    return col === 'unidades' ? { sigla: x.sigla, nome: x.nome, descricao: x.descricao }
      : col === 'fontes' ? { codigo: x.codigo, nome: x.nome, descricao: x.descricao }
        : { codigo: x.codigo, nome: x.nome, descricao: x.descricao, tipo: x.tipo, unidadeMedida: this.b.unidades.find((u) => u.id === x.unidadeMedidaId)?.sigla }
  }

  private preencherCalendario(x: Calendario, c: Record<string, any>, novo: boolean) {
    if (!texto(c.nome)) throw erroCampo('nome', 'Campo obrigatório.')
    if (this.b.calendarios.some((y) => y.id !== x.id && minusc(y.nome) === minusc(c.nome))) throw conflito('nome', 'Já existe um calendário com este nome.')
    try {
      new Intl.DateTimeFormat('pt-BR', { timeZone: c.fusoHorario })
    } catch {
      throw erroCampo('fusoHorario', 'Fuso horário inválido. Use o formato IANA, por exemplo America/Sao_Paulo.')
    }
    if (!c.vigenciaInicio) throw erroCampo('vigenciaInicio', 'Campo obrigatório.')
    if (!novo && c.vigenciaInicio < hoje()) throw erroCampo('vigenciaInicio', 'A alteração vale a partir da vigência informada: use hoje ou uma data futura.')
    const exp = (c.expedientes ?? []) as Expediente[]
    if (!exp.length) throw erroCampo('expedientes', 'Informe ao menos um intervalo de expediente.')
    for (let d = 1; d <= 7; d++) {
      const l = exp.filter((e) => e.diaSemana === d).sort((a, b) => a.horaInicio.localeCompare(b.horaInicio))
      for (const e of l) if (e.horaFim <= e.horaInicio) throw erroCampo('expedientes', 'O fim do intervalo de expediente deve ser posterior ao início.')
      for (let i = 1; i < l.length; i++) if (l[i]!.horaInicio < l[i - 1]!.horaFim) throw erroCampo('expedientes', 'Intervalos de expediente sobrepostos.')
    }
    const fer = (c.feriados ?? []) as Feriado[]
    const chaves = new Set<string>()
    for (const f of fer) {
      if (!f.data || !texto(f.descricao)) throw erroCampo('feriados', 'Informe data e descrição de cada feriado.')
      const k = f.recorrenteAnual ? f.data.slice(5) : f.data
      if (chaves.has(k)) throw erroCampo('feriados', `Feriado repetido em ${f.data}.`)
      chaves.add(k)
    }
    const norm = (h: string) => h.slice(0, 5)
    const expOrd = exp.map((e) => ({ diaSemana: e.diaSemana, horaInicio: norm(e.horaInicio), horaFim: norm(e.horaFim) }))
      .sort((a, b) => a.diaSemana - b.diaSemana || a.horaInicio.localeCompare(b.horaInicio))
    const minutos = expOrd.reduce((s, e) => s + (Number(e.horaFim.slice(0, 2)) * 60 + Number(e.horaFim.slice(3))) - (Number(e.horaInicio.slice(0, 2)) * 60 + Number(e.horaInicio.slice(3))), 0)
    Object.assign(x, { nome: c.nome.trim(), descricao: texto(c.descricao), fusoHorario: c.fusoHorario, vigenciaInicio: c.vigenciaInicio,
      expedientes: expOrd, feriados: fer.map((f) => ({ ...f, id: f.id ?? uuid(), descricao: f.descricao.trim() })).sort((a, b) => a.data.localeCompare(b.data)),
      horasSemanais: Math.round((minutos / 60) * 100) / 100 })
  }
  private snapCal(x: Calendario) {
    return { nome: x.nome, descricao: x.descricao, fusoHorario: x.fusoHorario, vigenciaInicio: x.vigenciaInicio,
      expediente: x.expedientes.map((e) => `${e.diaSemana} ${e.horaInicio}-${e.horaFim}`).join('; '),
      feriados: x.feriados.map((f) => `${f.data} ${f.descricao}${f.recorrenteAnual ? ' (anual)' : ''}`).join('; ') }
  }

  private preencherContratada(x: Banco['contratadas'][number], c: Record<string, any>) {
    const erro = validarCnpj(c.cnpj)
    if (erro) throw erroCampo('cnpj', erro)
    const cnpj = normalizarCnpj(c.cnpj)
    if (this.b.contratadas.some((y) => y.id !== x.id && y.cnpj === cnpj)) throw conflito('cnpj', `Já existe uma contratada com o CNPJ ${formatarCnpj(cnpj)}.`)
    if (!texto(c.razaoSocial)) throw erroCampo('razaoSocial', 'Campo obrigatório.')
    Object.assign(x, { cnpj, razaoSocial: c.razaoSocial.trim(), nomeFantasia: texto(c.nomeFantasia) })
  }

  private preencherContrato(x: ContratoDb, c: Record<string, any>) {
    for (const k of ['numero', 'objeto', 'contratadaId', 'calendarioId', 'vigenciaInicio', 'vigenciaFim']) if (!texto(c[k])) throw erroCampo(k, 'Campo obrigatório.')
    const numero = c.numero.trim().toUpperCase()
    if (this.b.contratos.some((y) => y.id !== x.id && y.numero === numero)) throw conflito('numero', `Já existe um contrato com o número ${numero}.`)
    if (c.vigenciaFim < c.vigenciaInicio) throw new Erro(422, 'regra-de-negocio', 'Regra de negócio não atendida', 'A data de fim da vigência não pode ser anterior à de início.', 'vigenciaFim')
    const k = this.b.contratadas.find((y) => y.id === c.contratadaId)
    if (!k) throw erroCampo('contratadaId', 'Contratada não encontrada.')
    if (k.situacao !== 'ATIVO' && x.contratadaId !== k.id) throw erroCampo('contratadaId', 'A contratada está desativada.')
    const cal = this.b.calendarios.find((y) => y.id === c.calendarioId)
    if (!cal || cal.situacao !== 'ATIVO') throw erroCampo('calendarioId', 'O calendário está desativado ou não existe.')
    const itens = (c.itens ?? []) as Array<{ servicoId: string; volume: number; valorUnitario: number }>
    if (!itens.length) throw erroCampo('itens', 'Informe ao menos um item contratado.')
    const vistos = new Set<string>()
    const novosItens = itens.map((i, idx) => {
      if (vistos.has(i.servicoId)) throw erroCampo(`itens[${idx}].servicoId`, 'O serviço foi informado em mais de um item.')
      vistos.add(i.servicoId)
      const s = this.b.servicos.find((y) => y.id === i.servicoId)
      if (!s) throw erroCampo(`itens[${idx}].servicoId`, 'Campo obrigatório.')
      if (s.situacao !== 'ATIVO') throw erroCampo(`itens[${idx}].servicoId`, `O serviço ${s.codigo} está desativado.`)
      if (!(Number(i.volume) > 0)) throw erroCampo(`itens[${idx}].volume`, 'O volume contratado deve ser maior que zero.')
      if (!(Number(i.valorUnitario) >= 0)) throw erroCampo(`itens[${idx}].valorUnitario`, 'O valor unitário não pode ser negativo.')
      return { id: uuid(), servicoId: s.id, unidadeMedidaId: s.unidadeMedidaId, volume: Number(i.volume), valorUnitario: Number(i.valorUnitario), valorTotal: centavos(Number(i.volume) * Number(i.valorUnitario)) }
    })
    const fontes = (c.fonteIds ?? []) as string[]
    if (!fontes.length) throw erroCampo('fonteIds', 'Informe ao menos uma fonte de recursos.')
    for (const f of fontes) {
      const fr = this.b.fontes.find((y) => y.id === f)
      if (!fr) throw erroCampo('fonteIds', 'Fonte de recursos não encontrada.')
      if (fr.situacao !== 'ATIVO' && !x.fonteIds?.includes(f)) throw erroCampo('fonteIds', `A fonte de recursos ${fr.codigo} está desativada.`)
    }
    const sistemas = [...new Set((c.sistemaIds ?? []) as string[])].map((id) => {
      const s = SISTEMAS.find((y) => y.id === id)
      if (!s) throw erroCampo('sistemaIds', `Sistema ${id} não encontrado no Portfólio.`)
      return { sistemaId: s.id, sistemaNome: s.nome }
    })
    Object.assign(x, { numero, objeto: c.objeto.trim(), contratadaId: k.id, calendarioId: cal.id, vigenciaInicio: c.vigenciaInicio, vigenciaFim: c.vigenciaFim,
      itens: novosItens, fonteIds: fontes, sistemas })
    if (x.createdAt && this.valorOriginal(x) + this.aditivosDe(x.id).reduce((s, a) => s + (a.valorAcrescimo ?? 0), 0) < 0) {
      throw erroCampo('itens', 'Os aditivos de supressão não podem tornar o valor do contrato negativo.')
    }
  }
  private snapContrato(x: ContratoDb) {
    return { numero: x.numero, objeto: x.objeto, contratada: this.b.contratadas.find((k) => k.id === x.contratadaId)?.razaoSocial,
      calendario: this.b.calendarios.find((k) => k.id === x.calendarioId)?.nome, vigenciaInicio: x.vigenciaInicio, vigenciaFim: x.vigenciaFim,
      itens: x.itens.map((i) => `${this.b.servicos.find((s) => s.id === i.servicoId)?.codigo}: ${i.volume} x ${i.valorUnitario}`).join('; '),
      fontes: x.fonteIds.map((f) => this.b.fontes.find((y) => y.id === f)?.codigo).sort().join(', '),
      sistemas: x.sistemas.map((s) => s.sistemaNome).sort().join(', ') }
  }

  private registrarAditivo(u: SessaoDb, x: ContratoDb, c: Record<string, any>) {
    if (x.situacao !== 'ATIVO') throw erroCampo('situacao', 'Não é possível aditar um contrato desativado.')
    if (!c.aditivoPrazo && !c.aditivoValor) throw erroCampo('aditivoPrazo', 'Marque aditivo de prazo, de valor ou ambos.')
    if (!texto(c.numero)) throw erroCampo('numero', 'Campo obrigatório.')
    if (this.aditivosDe(x.id).some((a) => minusc(a.numero) === minusc(c.numero))) throw conflito('numero', `O contrato já tem um aditivo com o número ${c.numero}.`)
    if (!c.dataAssinatura) throw erroCampo('dataAssinatura', 'Campo obrigatório.')
    if (c.dataAssinatura < x.vigenciaInicio) throw erroCampo('dataAssinatura', 'A assinatura do aditivo não pode ser anterior ao início da vigência.')
    if (!texto(c.justificativa)) throw erroCampo('justificativa', 'Campo obrigatório.')
    const fimAntes = this.fimEfetivo(x)
    const valorAntes = this.valorEfetivo(x)
    if (c.aditivoPrazo) {
      if (!c.novaVigenciaFim) throw erroCampo('novaVigenciaFim', 'Informe a nova data de fim da vigência.')
      if (c.novaVigenciaFim < x.vigenciaInicio) throw erroCampo('novaVigenciaFim', 'A nova data de fim não pode ser anterior ao início da vigência.')
      if (c.novaVigenciaFim === fimAntes) throw erroCampo('novaVigenciaFim', 'A nova data de fim é igual à vigência efetiva atual.')
    }
    if (c.aditivoValor) {
      if (!c.valorAcrescimo) throw erroCampo('valorAcrescimo', 'Informe o valor do aditivo (negativo para supressão).')
      if (valorAntes + Number(c.valorAcrescimo) < 0) throw erroCampo('valorAcrescimo', 'A supressão não pode tornar o valor do contrato negativo.')
    }
    this.b.aditivos.push({ id: uuid(), contratoId: x.id, numero: c.numero.trim(), aditivoPrazo: !!c.aditivoPrazo, aditivoValor: !!c.aditivoValor,
      novaVigenciaFim: c.aditivoPrazo ? c.novaVigenciaFim : null, valorAcrescimo: c.aditivoValor ? Number(c.valorAcrescimo) : null,
      dataAssinatura: c.dataAssinatura, justificativa: c.justificativa.trim(), createdAt: agoraIso(), createdByNome: u.nome })
    this.hist(u, 'contrato', x.id, 'ADITIVO', null, null, `Aditivo ${c.numero.trim()}${c.aditivoPrazo ? ` | vigência efetiva: ${fimAntes} -> ${this.fimEfetivo(x)}` : ''}${c.aditivoValor ? ` | valor efetivo: ${valorAntes} -> ${this.valorEfetivo(x)}` : ''}`, c.justificativa.trim())
  }

  private novoFiscal(contratoId: string, usuarioId: string, tipo: FiscalDb['tipoFiscal'], inicio: string, fim: string | null): FiscalDb {
    const uc = USUARIOS_CORE.find((y) => y.id === usuarioId)
    if (!uc) throw erroCampo('usuarioId', 'Usuário não encontrado no Core.')
    if (!tipo) throw erroCampo('tipoFiscal', 'Campo obrigatório.')
    if (!inicio) throw erroCampo('inicio', 'Informe o início da designação.')
    if (fim && fim < inicio) throw erroCampo('fim', 'O fim da designação não pode ser anterior ao início.')
    return { id: uuid(), contratoId, usuarioId: uc.id, nomeExibicao: uc.nomeExibicao, tipoFiscal: tipo, inicio, fim, motivoEncerramento: null, substituidoPorId: null }
  }

  private preencherSla(x: SlaDb, c: Record<string, any>, novo: boolean) {
    if (!texto(c.codigo)) throw erroCampo('codigo', 'Campo obrigatório.')
    if (!texto(c.nome)) throw erroCampo('nome', 'Campo obrigatório.')
    const codigo = c.codigo.trim().toUpperCase()
    if (this.b.slas.some((y) => y.id !== x.id && y.codigo === codigo)) throw conflito('codigo', `Já existe um SLA com o código ${codigo}.`)
    if (!c.tipoResponsavel) throw erroCampo('tipoResponsavel', 'Campo obrigatório.')
    Object.assign(x, { codigo, nome: c.nome.trim(), tipoResponsavel: c.tipoResponsavel })
    if (c.tipoResponsavel === 'CONTRATADA') {
      const k = this.b.contratos.find((y) => y.id === c.contratoId)
      if (!k) throw erroCampo('contratoId', 'Informe o contrato da contratada.')
      if ((novo || x.contratoId !== k.id) && !this.utilizavel(k)) throw erroCampo('contratoId', `O contrato ${k.numero} não está ativo ou está fora da vigência efetiva (RN01).`)
      Object.assign(x, { contratoId: k.id, contratadaId: k.contratadaId, equipeInterna: null })
    } else {
      if (!texto(c.equipeInterna)) throw erroCampo('equipeInterna', 'Informe a equipe interna responsável.')
      Object.assign(x, { contratoId: null, contratadaId: null, equipeInterna: c.equipeInterna.trim() })
    }
    const servicos = (c.servicoIds ?? []) as string[]
    if (!servicos.length) throw erroCampo('servicoIds', 'Informe ao menos um serviço monitorado pelo SLA.')
    const k = this.b.contratos.find((y) => y.id === x.contratoId)
    for (const sid of servicos) {
      const s = this.b.servicos.find((y) => y.id === sid)
      if (!s) throw erroCampo('servicoIds', 'Serviço não encontrado.')
      if (k && !k.itens.some((i) => i.servicoId === sid)) throw erroCampo('servicoIds', `O serviço ${s.codigo} não é item do contrato ${k.numero}.`)
    }
    x.servicoIds = servicos
    x.sistemas = [...new Set((c.sistemaIds ?? []) as string[])].map((id) => {
      const s = SISTEMAS.find((y) => y.id === id)
      if (!s) throw erroCampo('sistemaIds', `Sistema ${id} não encontrado no Portfólio.`)
      return { sistemaId: s.id, sistemaNome: s.nome }
    })
  }

  private regrasDe(regras: RegraSla[]): RegraDb[] {
    return regras.map((r, i) => {
      const campo = `regras[${i}]`
      if (!texto(r.classe)) throw erroCampo(`${campo}.classe`, 'Campo obrigatório.')
      if (!texto(r.indicador)) throw erroCampo(`${campo}.indicador`, 'Campo obrigatório.')
      const cal = r.calendarioId ? this.b.calendarios.find((y) => y.id === r.calendarioId) : null
      if (cal && cal.situacao !== 'ATIVO') throw erroCampo(`${campo}.calendarioId`, `O calendário ${cal.nome} está desativado.`)
      regra(campo, () => validarRegra(r.formaContagem, r.metaUnidade, Number(r.metaValor), !!cal))
      return { id: uuid(), ordem: i + 1, classe: r.classe.trim(), indicador: r.indicador.trim(), metaValor: Number(r.metaValor), metaUnidade: r.metaUnidade,
        formaContagem: r.formaContagem, calendarioId: r.formaContagem === 'HORARIO_COMERCIAL' ? (cal?.id ?? null) : null, permitePausa: !!r.permitePausa, observacao: texto(r.observacao) }
    })
  }

  private novaVersaoDb(slaId: string, numero: number, motivo: string): VersaoDb {
    return { id: uuid(), slaId, numero, situacao: 'RASCUNHO', vigenciaInicio: null, vigenciaFim: null, motivoAlteracao: motivo, parecerFavoravel: null,
      parecerTexto: null, parecerPorNome: null, parecerEm: null, decisaoObservacao: null, decisaoPorNome: null, decisaoEm: null, regras: [], version: 0 }
  }

  private preencherNotificacao(x: Notificacao, c: Record<string, any>) {
    for (const k of ['nome', 'evento', 'assunto', 'modelo']) if (!texto(c[k])) throw erroCampo(k, 'Campo obrigatório.')
    if (!/^[a-z]+\.[a-z]+(-[a-z]+)*\.[a-z]+(-[a-z]+)*$/.test(c.evento)) throw erroCampo('evento', 'Use o padrão modulo.entidade.fato-no-passado, por exemplo sustentacao.sla.violado.')
    const ev = CATALOGO.find((e) => e.evento === c.evento)
    if (!ev) throw erroCampo('evento', `Evento ${c.evento} não está no catálogo de notificações.`)
    if (!c.canalApp && !c.canalEmail) throw erroCampo('canalApp', 'Escolha ao menos um canal: na ferramenta ou por e-mail.')
    const perfis = (c.perfis ?? []) as string[]
    if (!perfis.length) throw erroCampo('perfis', 'Campo obrigatório.')
    const inv = perfis.filter((p) => !PERFIS.includes(p))
    if (inv.length) throw erroCampo('perfis', `Perfis inexistentes: ${inv.join(', ')}.`)
    const limiar = c.limiarPercentual === '' || c.limiarPercentual === null || c.limiarPercentual === undefined ? null : Number(c.limiarPercentual)
    if (ev.exigeLimiar && (limiar === null || limiar < 1 || limiar > 99)) throw erroCampo('limiarPercentual', 'Informe o limiar de alerta entre 1% e 99% do prazo consumido.')
    if (!ev.exigeLimiar && limiar !== null) throw erroCampo('limiarPercentual', 'O limiar de alerta só se aplica ao evento sustentacao.sla.em-risco.')
    const vars = [...`${c.assunto} ${c.modelo}`.matchAll(/\{\{\s*([a-zA-Z][a-zA-Z0-9_.]*)\s*}}/g)].map((m) => m[1]!)
    const desc = [...new Set(vars.filter((v) => !ev.variaveis.includes(v)))]
    if (desc.length) throw erroCampo('modelo', `Variáveis não disponíveis para este evento: ${desc.join(', ')}. Disponíveis: ${ev.variaveis.join(', ')}.`)
    if (c.canalEmail) {
      const proib = [...new Set(vars.filter((v) => ['cpf', 'descricao', 'conteudo', 'comentario', 'mensagem', 'anexo', 'telefone', 'email'].includes(v.split('.').pop()!.toLowerCase())))]
      if (proib.length) throw erroCampo('modelo', `Mensagens por e-mail não podem exibir CPF nem conteúdo de chamados: ${proib.join(', ')}.`)
    }
    Object.assign(x, { nome: c.nome.trim(), evento: c.evento, canalApp: !!c.canalApp, canalEmail: !!c.canalEmail, perfis, assunto: c.assunto.trim(), modelo: c.modelo.trim(), limiarPercentual: limiar })
  }

  // ------------------------------------------------------------ painel, eventos e auditoria
  private painel(u: SessaoDb) {
    const h = hoje()
    const rest = this.restrito(u)
    const ativos = this.b.contratos.filter((c) => c.situacao === 'ATIVO' && (!rest || c.contratadaId === u.contratadaId))
    const totais: Record<string, number> = { contratosAtivos: ativos.length, contratosVigentes: ativos.filter((c) => this.utilizavel(c)).length }
    if (!rest) Object.assign(totais, { contratadas: this.b.contratadas.length, slas: this.b.slas.length, servicos: this.b.servicos.length,
      unidadesMedida: this.b.unidades.length, fontesRecurso: this.b.fontes.length, calendarios: this.b.calendarios.length, notificacoes: this.b.notificacoes.length })
    const pend: Array<{ tipo: string; descricao: string; recurso: string; recursoId: string | null }> = []
    for (const c of ativos) {
      if (this.utilizavel(c) && !this.fiscaisDe(c.id).some((f) => vigenteEm(h, f.inicio, f.fim))) pend.push({ tipo: 'CONTRATO_SEM_FISCAL', descricao: `Contrato ${c.numero} sem fiscal com designação vigente (RN06).`, recurso: 'contrato', recursoId: c.id })
      const fim = this.fimEfetivo(c)
      if (fim >= h && fim <= somarDias(h, 60)) pend.push({ tipo: 'CONTRATO_VENCENDO', descricao: `Contrato ${c.numero} termina em ${fim}.`, recurso: 'contrato', recursoId: c.id })
    }
    for (const v of this.b.versoes.filter((y) => y.situacao === 'EM_AVALIACAO')) {
      const s = this.b.slas.find((y) => y.id === v.slaId)!
      if (rest && s.contratadaId !== u.contratadaId) continue
      pend.push({ tipo: 'SLA_EM_AVALIACAO', descricao: `SLA ${s.codigo} versão ${v.numero} aguardando ${v.parecerFavoravel === null ? 'parecer da contratada' : 'decisão de vigência'}.`, recurso: 'sla', recursoId: s.id })
    }
    let semSla = 0
    if (!rest) {
      semSla = SISTEMAS.filter((s) => s.situacao === 'ATIVO' && !this.b.slas.some((k) => k.situacao === 'ATIVO' && k.sistemas.some((y) => y.sistemaId === s.id) && this.vigenteEmData(k.id, h))).length
      if (semSla) pend.push({ tipo: 'SISTEMA_SEM_SLA', descricao: `${semSla} sistema(s) ativo(s) no Portfólio sem SLA vigente (RN20).`, recurso: 'portfolio', recursoId: null })
    }
    if (!rest) totais.sistemasSemSla = semSla
    return { totais, pendencias: pend, eventosPendentes: 0, portfolioDisponivel: true, dataReferencia: h }
  }

  private consumirEvento(c: Record<string, any>, u: SessaoDb) {
    if (!c.id || !c.type) throw erroCampo('id', 'Informe id e type do evento.')
    if (this.b.eventosProcessados.includes(c.id)) return { id: c.id, processado: false, mensagem: 'Evento já processado (ignorado).' }
    let msg = 'Evento sem efeito neste módulo.'
    const sid = c.payload?.sistemaId
    if (String(c.type).startsWith('portfolio.sistema.') && sid) {
      let n = 0
      if (c.payload.nome) {
        for (const k of [...this.b.contratos, ...this.b.slas]) for (const s of k.sistemas) if (s.sistemaId === sid && s.sistemaNome !== c.payload.nome) {
          s.sistemaNome = c.payload.nome
          n++
        }
      }
      msg = `Nomes de exibição atualizados: ${n}.`
      if (c.type === 'portfolio.sistema.ativado' && !this.b.slas.some((k) => k.situacao === 'ATIVO' && k.sistemas.some((y) => y.sistemaId === sid) && this.vigenteEmData(k.id, hoje()))) {
        msg += ' Sistema sem SLA vigente: pendência de configuração sinalizada.'
      }
    }
    this.b.eventosProcessados.push(c.id)
    this.hist(u, 'evento', c.id, 'EVENTO_CONSUMIDO', null, null, `${c.type}: ${msg}`)
    return { id: c.id, processado: true, mensagem: msg }
  }

  private auditoria(metodo: string, rota: string, q: URLSearchParams, c: Record<string, any> | undefined, u: SessaoDb) {
    if (!u.perfis.some((p) => GESTAO.includes(p))) throw proibido('A auditoria é restrita a Administrador e Gestor de TI.')
    let r: RegExpMatchArray | null
    if (rota === '/acessos') {
      const f = this.b.trilha.filter((t) => (!q.get('usuarioId') || t.usuarioId === q.get('usuarioId')) && (!q.get('modulo') || t.modulo === q.get('modulo'))
        && (!q.get('recurso') || t.recurso === q.get('recurso')) && (!q.get('acao') || t.acao === q.get('acao'))
        && (!q.get('de') || t.ocorridoEm >= q.get('de')!) && (!q.get('ate') || t.ocorridoEm < q.get('ate')!)
        && (q.get('dadoPessoal') === null || String(t.dadoPessoal) === q.get('dadoPessoal')))
      return { status: 200, dados: paginar(f, q, { ocorridoEm: (x) => x.ocorridoEm }, 'ocorridoEm,desc') }
    }
    if (rota === '/exportacoes' && metodo === 'POST') {
      const tipo = c?.tipo
      if (tipo !== 'ACESSOS' && tipo !== 'GOVERNANCA') throw erroCampo('tipo', 'Campo obrigatório.')
      const filtros = (c?.filtros ?? {}) as Record<string, string>
      const linhas = tipo === 'ACESSOS'
        ? ['ocorrido_em;usuario_id;usuario_nome;perfis;metodo;rota;modulo;recurso;acao;status;ip;correlation_id;dado_pessoal',
          ...this.b.trilha.filter((t) => !filtros.usuarioId || t.usuarioId === filtros.usuarioId).map((t) => [t.ocorridoEm, t.usuarioId, t.usuarioNome, t.perfis, t.metodo, t.rota, t.modulo, t.recurso, t.acao, t.status, t.ip, t.correlationId, t.dadoPessoal ? 'S' : 'N'].map((v) => String(v ?? '').replace(/;/g, ',')).join(';'))]
        : ['ocorrido_em;usuario_id;usuario_nome;entidade;entidade_id;acao;campo;valor_anterior;valor_novo;justificativa',
          ...this.b.historico.filter((h) => (!filtros.entidade || h.entidade === filtros.entidade) && (!filtros.acao || h.acao === filtros.acao)).map((h) => [h.ocorridoEm, h.usuarioId, h.usuarioNome, h.entidade, h.entidadeId, h.acao, h.campo, h.valorAnterior, h.valorNovo, h.justificativa].map((v) => String(v ?? '').replace(/[;\n]/g, ',')).join(';'))]
      const id = uuid()
      const exp = { id, tipo, filtros: new URLSearchParams(filtros).toString(), situacao: 'PROCESSANDO' as const, criadoEm: agoraIso(), concluidoEm: null as string | null,
        totalRegistros: null as number | null, expiraEm: null as string | null, mensagemErro: null, solicitanteId: u.id, conteudo: '﻿' + linhas.join('\n'), tokenLink: null }
      this.b.exportacoes.unshift(exp)
      setTimeout(() => {
        this.b = carregar() ?? this.b
        const alvo = this.b.exportacoes.find((x) => x.id === id)
        if (alvo) Object.assign(alvo, { situacao: 'CONCLUIDA', concluidoEm: agoraIso(), totalRegistros: linhas.length - 1, expiraEm: new Date(Date.now() + 86_400_000).toISOString() })
        salvar(this.b)
      }, 1500)
      return { status: 202, dados: { id, situacao: 'PROCESSANDO', mensagem: 'Exportação em processamento. Você será avisado quando o arquivo estiver pronto.' } }
    }
    if (rota === '/exportacoes') {
      return { status: 200, dados: this.b.exportacoes.filter((e) => e.solicitanteId === u.id).slice(0, 20).map(({ conteudo: _c, tokenLink: _t, solicitanteId: _s, ...e }) => e) }
    }
    if ((r = rota.match(/^\/exportacoes\/([^/]+)\/link$/))) {
      const e = this.b.exportacoes.find((x) => x.id === r![1] && x.solicitanteId === u.id && x.situacao === 'CONCLUIDA')
      if (!e) throw new Erro(404, 'exportacao-indisponivel', 'Exportação indisponível', 'A exportação não existe, não terminou ou o link expirou.')
      e.tokenLink = uuid().replace(/-/g, '')
      return { status: 200, dados: { url: `/api/v1/auditoria/exportacoes/${e.id}/arquivo?token=${e.tokenLink}` } }
    }
    if ((r = rota.match(/^\/exportacoes\/([^/]+)\/arquivo$/))) {
      const e = this.b.exportacoes.find((x) => x.id === r![1] && x.solicitanteId === u.id)
      if (!e || !e.tokenLink || e.tokenLink !== q.get('token') || !e.expiraEm || e.expiraEm < agoraIso()) throw new Erro(404, 'link-invalido', 'Link inválido ou expirado', 'Gere um novo link na lista de exportações.')
      return { status: 200, blob: new Blob([e.conteudo ?? ''], { type: 'text/csv;charset=utf-8' }) }
    }
    throw new Erro(404, 'rota-inexistente', 'Rota inexistente', 'A rota solicitada não existe.')
  }

  // ------------------------------------------------------------ carga fictícia (mesma do CargaDemonstracao.java)
  private semear() {
    const admin: SessaoDb = { id: USUARIOS_DEMO[0]!.id, nome: 'Carga de demonstração', cpf: '', perfis: ['ADMINISTRADOR', 'GESTOR_TI'], contratadaId: null, loginEm: agoraIso() }
    this.b.sessao = admin
    const post = (rota: string, corpo: unknown, chave?: string) => this.modulo('POST', rota, new URLSearchParams(), { corpo, chaveIdempotencia: chave }, this.b.sessao!).dados as any
    const pf = post('/unidades-medida', { sigla: 'PF', nome: 'Ponto de Função', descricao: 'Medida funcional (IFPUG/SISP). Apurada pela Engenharia: PF estimado e realizado.' })
    const hora = post('/unidades-medida', { sigla: 'H', nome: 'Hora técnica', descricao: 'Hora de trabalho técnico.' })
    const ust = post('/unidades-medida', { sigla: 'UST', nome: 'Unidade de Serviço Técnico', descricao: 'Unidade para serviços técnicos de sustentação.' })
    const tesouro = post('/fontes-recurso', { codigo: 'FR-1500', nome: 'Recursos ordinários do tesouro', descricao: 'Fonte fictícia para demonstração.' })
    const convenio = post('/fontes-recurso', { codigo: 'FR-1700', nome: 'Convênios e transferências', descricao: 'Fonte fictícia para demonstração.' })
    post('/fontes-recurso', { codigo: 'FR-1759', nome: 'Operação de crédito (programa de modernização)' })
    const dev = post('/servicos', { codigo: 'DEV-PF', nome: 'Desenvolvimento de software', descricao: 'Novas funcionalidades medidas em PF.', tipo: 'DESENVOLVIMENTO', unidadeMedidaId: pf.id })
    const man = post('/servicos', { codigo: 'MAN-PF', nome: 'Manutenção evolutiva e corretiva', tipo: 'MANUTENCAO', unidadeMedidaId: pf.id })
    const sus = post('/servicos', { codigo: 'SUS-H', nome: 'Sustentação de sistemas', descricao: 'Atendimento N2/N2+ de chamados.', tipo: 'SUSTENTACAO', unidadeMedidaId: hora.id })
    const sup = post('/servicos', { codigo: 'SUP-UST', nome: 'Suporte técnico especializado', tipo: 'SUSTENTACAO', unidadeMedidaId: ust.id })
    const exp: Expediente[] = []
    for (let d = 1; d <= 5; d++) exp.push({ diaSemana: d, horaInicio: '08:00', horaFim: '12:00' }, { diaSemana: d, horaInicio: '14:00', horaFim: '18:00' })
    const fer = (data: string, descricao: string, abrangencia: Feriado['abrangencia'], recorrenteAnual: boolean): Feriado => ({ data, descricao, abrangencia, recorrenteAnual })
    const comercial = post('/calendarios', { nome: 'Expediente administrativo', descricao: 'Segunda a sexta, 8h às 12h e 14h às 18h, feriados nacionais.', fusoHorario: 'America/Sao_Paulo',
      vigenciaInicio: '2026-01-01', expedientes: exp, feriados: [fer('2026-01-01', 'Confraternização Universal', 'NACIONAL', true), fer('2026-04-21', 'Tiradentes', 'NACIONAL', true),
        fer('2026-05-01', 'Dia do Trabalho', 'NACIONAL', true), fer('2026-09-07', 'Independência do Brasil', 'NACIONAL', true), fer('2026-10-12', 'Nossa Senhora Aparecida', 'NACIONAL', true),
        fer('2026-11-02', 'Finados', 'NACIONAL', true), fer('2026-11-15', 'Proclamação da República', 'NACIONAL', true), fer('2026-11-20', 'Dia Nacional de Zumbi e da Consciência Negra', 'NACIONAL', true),
        fer('2026-12-25', 'Natal', 'NACIONAL', true), fer('2027-02-08', 'Carnaval', 'PONTO_FACULTATIVO', false), fer('2027-02-09', 'Carnaval', 'PONTO_FACULTATIVO', false),
        fer('2027-03-26', 'Sexta-feira Santa', 'NACIONAL', false)] })
    post('/calendarios', { nome: 'Plantão estendido', descricao: 'Todos os dias, 7h às 22h.', fusoHorario: 'America/Sao_Paulo', vigenciaInicio: '2026-01-01',
      expedientes: [1, 2, 3, 4, 5, 6, 7].map((d) => ({ diaSemana: d, horaInicio: '07:00', horaFim: '22:00' })), feriados: [] })
    const contratada = (id: string, base: string, razao: string, fantasia: string) => {
      this.b.contratadas.push({ id, cnpj: base + calcularDigitosCnpj(base), razaoSocial: razao, nomeFantasia: fantasia, situacao: 'ATIVO', justificativaSituacao: null, version: 0, createdAt: agoraIso(), updatedAt: null })
      this.criacao(admin, 'contratada', id, { cnpj: formatarCnpj(base + calcularDigitosCnpj(base)), razaoSocial: razao, nomeFantasia: fantasia })
    }
    contratada(FABRICA_ALFA, '112223330001', 'Fábrica Alfa Tecnologia Ltda.', 'Fábrica Alfa')
    contratada(FABRICA_BETA, '12ABC34501DE', 'Fábrica Beta Sistemas S.A.', 'Fábrica Beta')
    post(`/contratadas/${FABRICA_ALFA}/equipe`, { usuarioId: '10000000-0000-4000-8000-000000000004', papel: 'N2_GESTOR', inicio: '2026-01-01' })
    post(`/contratadas/${FABRICA_ALFA}/equipe`, { usuarioId: '10000000-0000-4000-8000-000000000005', papel: 'N2_TECNICO', inicio: '2026-01-01' })
    post(`/contratadas/${FABRICA_BETA}/equipe`, { usuarioId: '10000000-0000-4000-8000-000000000006', papel: 'N2_GESTOR', inicio: '2026-01-01' })
    post(`/contratadas/${FABRICA_BETA}/alteracoes`, { dataAlteracao: '2026-07-15', tipo: 'ALTERACAO_SOCIETARIA', descricao: 'Ingresso de novo sócio administrador (dado fictício).' })
    const ctAlfa = post('/contratos', { numero: 'CT-2026-0007', objeto: 'Desenvolvimento, manutenção e sustentação dos sistemas da Receita.', contratadaId: FABRICA_ALFA,
      calendarioId: comercial.id, vigenciaInicio: '2026-01-15', vigenciaFim: '2027-01-14',
      itens: [{ servicoId: dev.id, volume: 3000, valorUnitario: 520 }, { servicoId: man.id, volume: 1500, valorUnitario: 480 }, { servicoId: sus.id, volume: 8760, valorUnitario: 95.5 }],
      fonteIds: [tesouro.id], sistemaIds: [SISTEMAS[0]!.id, SISTEMAS[1]!.id],
      fiscais: [{ usuarioId: '10000000-0000-4000-8000-000000000007', tipoFiscal: 'FISCAL_TECNICO', inicio: '2026-01-15' }, { usuarioId: '10000000-0000-4000-8000-000000000008', tipoFiscal: 'FISCAL_ADMINISTRATIVO', inicio: '2026-01-15' }] })
    post(`/contratos/${ctAlfa.id}/aditivos`, { numero: '1º TA', aditivoPrazo: false, aditivoValor: true, valorAcrescimo: 120000, dataAssinatura: '2026-06-10', justificativa: 'Acréscimo de 9,6% no volume de desenvolvimento.' })
    const ctBeta = post('/contratos', { numero: 'CT-2025-0031', objeto: 'Sustentação do Portal do Cidadão e da Folha de Pagamento.', contratadaId: FABRICA_BETA,
      calendarioId: comercial.id, vigenciaInicio: '2025-03-01', vigenciaFim: '2026-02-28', itens: [{ servicoId: sup.id, volume: 12000, valorUnitario: 70 }],
      fonteIds: [convenio.id], sistemaIds: [SISTEMAS[2]!.id], fiscais: [{ usuarioId: '10000000-0000-4000-8000-000000000010', tipoFiscal: 'GESTOR_CONTRATO', inicio: '2025-03-01' }] })
    post(`/contratos/${ctBeta.id}/aditivos`, { numero: '1º TA', aditivoPrazo: true, aditivoValor: false, novaVigenciaFim: '2027-02-28', dataAssinatura: '2026-02-20', justificativa: 'Prorrogação por 12 meses (art. 107 da Lei 14.133/2021).' })
    const slaAlfa = post('/slas', { codigo: 'SLA-ALFA', nome: 'SLA de sustentação - Fábrica Alfa', tipoResponsavel: 'CONTRATADA', contratoId: ctAlfa.id, servicoIds: [sus.id], sistemaIds: [SISTEMAS[0]!.id],
      regras: [{ classe: 'Prioridade crítica', indicador: 'Tempo de primeira resposta', metaValor: 30, metaUnidade: 'MINUTOS', formaContagem: 'CORRIDO_24X7', permitePausa: false },
        { classe: 'Prioridade crítica', indicador: 'Tempo de solução', metaValor: 4, metaUnidade: 'HORAS', formaContagem: 'HORARIO_COMERCIAL', calendarioId: comercial.id, permitePausa: true },
        { classe: 'Prioridade normal', indicador: 'Tempo de solução', metaValor: 3, metaUnidade: 'DIAS_UTEIS', formaContagem: 'HORARIO_COMERCIAL', calendarioId: comercial.id, permitePausa: true },
        { classe: 'Serviço', indicador: 'Disponibilidade mensal', metaValor: 99.5, metaUnidade: 'PERCENTUAL', formaContagem: 'CORRIDO_24X7', permitePausa: false, observacao: 'Medida mensal' }] })
    const v1 = slaAlfa.versoes[0].id
    post(`/slas/${slaAlfa.id}/versoes/${v1}/envio-avaliacao`, {})
    this.b.sessao = { ...admin, id: USUARIOS_DEMO[3]!.id, nome: USUARIOS_DEMO[3]!.nome, perfis: ['N2_GESTOR'], contratadaId: FABRICA_ALFA }
    post(`/slas/${slaAlfa.id}/versoes/${v1}/parecer`, { favoravel: true, texto: 'De acordo com as metas propostas.' })
    this.b.sessao = { ...admin, id: USUARIOS_DEMO[1]!.id, nome: USUARIOS_DEMO[1]!.nome, perfis: ['GESTOR_TI'] }
    post(`/slas/${slaAlfa.id}/versoes/${v1}/decisao`, { aprovar: true, vigenciaInicio: hoje(), observacao: 'Aprovado na reunião de alinhamento.' })
    post('/slas', { codigo: 'SLA-N1', nome: 'SLA da equipe interna (N1)', tipoResponsavel: 'EQUIPE_INTERNA', equipeInterna: 'Central de atendimento N1', servicoIds: [sus.id],
      sistemaIds: [SISTEMAS[3]!.id], regras: [{ classe: 'Todas', indicador: 'Tempo de triagem', metaValor: 2, metaUnidade: 'HORAS', formaContagem: 'HORARIO_COMERCIAL', calendarioId: comercial.id, permitePausa: true }] })
    this.b.sessao = admin
    post('/notificacoes', { nome: 'Alerta de SLA em risco', evento: 'sustentacao.sla.em-risco', canalApp: true, canalEmail: true, perfis: ['N1', 'N2_GESTOR'],
      assunto: 'Chamado {{chamado.codigo}} com SLA em risco', modelo: 'O chamado {{chamado.codigo}} consumiu {{sla.percentual}} do prazo do {{sla.codigo}}. Prazo: {{sla.prazo}}.', limiarPercentual: 80 })
    post('/notificacoes', { nome: 'SLA violado', evento: 'sustentacao.sla.violado', canalApp: true, canalEmail: true, perfis: ['GESTOR_TI', 'N2_GESTOR'],
      assunto: 'SLA violado: {{chamado.codigo}}', modelo: 'O prazo do {{sla.codigo}} para o chamado {{chamado.codigo}} venceu em {{sla.prazo}}.' })
    post('/notificacoes', { nome: 'Versão de SLA em avaliação', evento: 'configuracao.sla.em-avaliacao', canalApp: true, canalEmail: false, perfis: ['N2_GESTOR'],
      assunto: 'SLA {{sla.codigo}} aguardando seu parecer', modelo: 'A versão {{sla.versao}} do {{sla.nome}} foi enviada para avaliação.' })
  }
}

function exigir(u: SessaoDb, perfis: string[]) {
  if (!u.perfis.some((p) => perfis.includes(p))) throw proibido()
}
