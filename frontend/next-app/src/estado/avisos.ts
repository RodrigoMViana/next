import { reactive } from 'vue'
import { ProblemaError } from '@/api/cliente'

export interface Aviso {
  id: number
  tipo: 'sucesso' | 'erro' | 'aviso'
  texto: string
  detalhe?: string
}

export const avisos = reactive<Aviso[]>([])
let seq = 0

export function avisar(texto: string, tipo: Aviso['tipo'] = 'sucesso', detalhe?: string) {
  const a = { id: ++seq, tipo, texto, detalhe }
  avisos.push(a)
  // Toast some em 5 s, exceto erros (12.8)
  if (tipo !== 'erro') setTimeout(() => fecharAviso(a.id), 5000)
}

export function fecharAviso(id: number) {
  const i = avisos.findIndex((a) => a.id === id)
  if (i >= 0) avisos.splice(i, 1)
}

/** Mostra o erro de uma operação (Problem Details com correlationId para o suporte). */
export function avisarErro(e: unknown) {
  if (e instanceof ProblemaError) {
    avisar(e.problema.title, 'erro', `${e.problema.detail}${e.problema.correlationId ? ` (código ${e.problema.correlationId})` : ''}`)
  } else {
    avisar('Erro inesperado', 'erro', (e as Error)?.message)
  }
}
