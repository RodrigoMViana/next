import { transporteHttp, type Opcoes, type Resposta, type Transporte } from './cliente'
import { BffDemo } from './demo/bffDemo'

/**
 * Seleção do transporte:
 *  - "bff"  (padrão): chamadas reais ao next-bff na mesma origem.
 *  - "demo": BFF simulado no navegador, usado na publicação estática (GitHub Pages).
 */
export const MODO: 'bff' | 'demo' = import.meta.env.VITE_MODO === 'demo' ? 'demo' : 'bff'

let demo: BffDemo | null = null
const transporte: Transporte = MODO === 'demo' ? (demo = new BffDemo()) : transporteHttp

export const API = '/api/v1'
export const CFG = '/api/v1/configuracao'

export const api = {
  get: <T>(caminho: string, query?: Opcoes['query']): Promise<Resposta<T>> => transporte.requisitar<T>('GET', caminho, { query }),
  post: <T>(caminho: string, corpo?: unknown, op: Omit<Opcoes, 'corpo'> = {}) => transporte.requisitar<T>('POST', caminho, { ...op, corpo: corpo ?? {} }),
  put: <T>(caminho: string, corpo: unknown, ifMatch?: string) => transporte.requisitar<T>('PUT', caminho, { corpo, ifMatch }),
  del: <T>(caminho: string) => transporte.requisitar<T>('DELETE', caminho),
  enviarArquivo: <T>(caminho: string, formData: FormData) => transporte.requisitar<T>('POST', caminho, { formData }),
  baixar: async (caminho: string, query?: Opcoes['query']): Promise<Blob> => (await transporte.requisitar('GET', caminho, { query }, true)).blob!,
}

/** Restaura os dados fictícios do modo demonstração. */
export function restaurarDemonstracao() {
  demo?.restaurar()
}

export function salvarArquivo(blob: Blob, nome: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nome
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export const novaChave = (): string =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : String(Date.now()) + Math.random()
