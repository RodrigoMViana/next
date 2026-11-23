import { reactive, ref, watch } from 'vue'
import { api } from '@/api'
import type { Pagina } from '@/api/tipos'

/** Lista paginada com filtros, ordenação e estados de carregamento/erro (RF01, RNF07). */
export function useLista<T>(caminho: string, filtrosIniciais: Record<string, string> = {}, ordemInicial = '') {
  const filtros = reactive<Record<string, string>>({ ...filtrosIniciais })
  const pagina = ref<Pagina<T>>({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  const carregando = ref(true)
  const erro = ref<string | null>(null)
  const ordem = ref(ordemInicial)
  let atual = 0

  async function carregar(page = 0) {
    carregando.value = true
    erro.value = null
    const meu = ++atual
    try {
      const r = await api.get<Pagina<T>>(caminho, { ...filtros, page, size: 20, sort: ordem.value || undefined })
      if (meu === atual) pagina.value = r.dados
    } catch (e) {
      if (meu === atual) erro.value = (e as Error).message
    } finally {
      if (meu === atual) carregando.value = false
    }
  }

  function ordenar(campo: string) {
    const [c, d] = ordem.value.split(',')
    ordem.value = c === campo && d !== 'desc' ? `${campo},desc` : `${campo},asc`
    carregar(0)
  }

  let espera: ReturnType<typeof setTimeout> | undefined
  watch(filtros, () => {
    clearTimeout(espera)
    espera = setTimeout(() => carregar(0), 300)
  })

  return { filtros, pagina, carregando, erro, carregar, ordem, ordenar }
}
