import type { ProblemDetails } from './tipos'

/** Erro com corpo Problem Details (RFC 9457) devolvido pelo BFF. */
export class ProblemaError extends Error {
  constructor(public readonly problema: ProblemDetails) {
    super(problema.detail || problema.title)
  }
  get status() {
    return this.problema.status
  }
  /** Mensagens por campo (para aria-describedby nos formulários). */
  get campos(): Record<string, string> {
    const r: Record<string, string> = {}
    for (const e of this.problema.errors ?? []) {
      const chave = e.field.replace(/\[\d+\]\..*$/, (m) => m)
      if (!r[chave]) r[chave] = e.message
    }
    return r
  }
}

export interface Opcoes {
  corpo?: unknown
  formData?: FormData
  query?: Record<string, string | number | boolean | null | undefined>
  ifMatch?: string
  chaveIdempotencia?: string
}

export interface Resposta<T = unknown> {
  status: number
  dados: T
  etag?: string
  cabecalhos: Record<string, string>
  blob?: Blob
}

/** Transporte: HTTP real (BFF) ou BFF simulado no navegador (modo demonstração). */
export interface Transporte {
  requisitar<T>(metodo: string, caminho: string, opcoes?: Opcoes, binario?: boolean): Promise<Resposta<T>>
  readonly modo: 'bff' | 'demo'
}

export function montarQuery(query?: Opcoes['query']): string {
  if (!query) return ''
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v !== null && v !== undefined && v !== '') p.set(k, String(v))
  }
  const s = p.toString()
  return s ? `?${s}` : ''
}

function cookie(nome: string): string | null {
  const m = document.cookie.match(new RegExp('(?:^|; )' + nome + '=([^;]*)'))
  return m ? decodeURIComponent(m[1]!) : null
}

let aoExpirarSessao: () => void = () => {}
export function quandoSessaoExpirar(fn: () => void) {
  aoExpirarSessao = fn
}
export function notificarSessaoExpirada() {
  aoExpirarSessao()
}

/** Cliente HTTP único: mesma origem, cookie de sessão HttpOnly, cabeçalho anti-CSRF em toda escrita. */
export const transporteHttp: Transporte = {
  modo: 'bff',
  async requisitar<T>(metodo: string, caminho: string, op: Opcoes = {}, binario = false): Promise<Resposta<T>> {
    const cab: Record<string, string> = { Accept: 'application/json' }
    if (metodo !== 'GET') {
      let token = cookie('XSRF-TOKEN')
      if (!token) {
        await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
        token = cookie('XSRF-TOKEN')
      }
      if (token) cab['X-XSRF-TOKEN'] = token
    }
    if (op.ifMatch) cab['If-Match'] = op.ifMatch
    if (op.chaveIdempotencia) cab['Idempotency-Key'] = op.chaveIdempotencia
    let body: BodyInit | undefined
    if (op.formData) body = op.formData
    else if (op.corpo !== undefined) {
      cab['Content-Type'] = 'application/json'
      body = JSON.stringify(op.corpo)
    }
    const r = await fetch(caminho + montarQuery(op.query), { method: metodo, headers: cab, body, credentials: 'same-origin' })
    const cabecalhos: Record<string, string> = {}
    r.headers.forEach((v, k) => (cabecalhos[k] = v))
    const tipo = r.headers.get('content-type') ?? ''
    if (!r.ok) {
      let problema: ProblemDetails
      if (tipo.includes('json')) problema = await r.json()
      else
        problema = {
          type: 'urn:next:erro:http',
          title: 'Falha na comunicação',
          status: r.status,
          detail: r.status === 0 ? 'Sem conexão com o servidor.' : `O servidor respondeu ${r.status}.`,
        }
      if (r.status === 401 && !caminho.startsWith('/api/v1/auth/')) notificarSessaoExpirada()
      throw new ProblemaError(problema)
    }
    if (binario) return { status: r.status, dados: null as T, cabecalhos, blob: await r.blob() }
    const dados = r.status === 204 || !tipo.includes('json') ? (null as T) : ((await r.json()) as T)
    return { status: r.status, dados, etag: r.headers.get('etag') ?? undefined, cabecalhos }
  },
}
