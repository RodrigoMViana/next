import { reactive, readonly } from 'vue'
import { api, API } from '@/api'
import type { Sessao } from '@/api/tipos'

const estado = reactive<{ usuario: Sessao | null; carregada: boolean }>({ usuario: null, carregada: false })

export const sessao = readonly(estado)

export async function carregarSessao(): Promise<Sessao | null> {
  try {
    const r = await api.get<Sessao>(`${API}/auth/sessao`)
    estado.usuario = r.dados
  } catch {
    estado.usuario = null
  } finally {
    estado.carregada = true
  }
  return estado.usuario
}

export async function entrar(usuarioId: string, codigoMfa?: string): Promise<Sessao> {
  const r = await api.post<Sessao>(`${API}/auth/login`, { usuarioId, codigoMfa: codigoMfa || undefined })
  estado.usuario = r.dados
  return r.dados
}

export async function sair() {
  try {
    await api.post(`${API}/auth/logout`)
  } finally {
    estado.usuario = null
  }
}

export function limparSessao() {
  estado.usuario = null
}

export const pode = (permissao: string): boolean => !!estado.usuario?.permissoes?.[permissao]
