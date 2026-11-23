<script setup lang="ts">
/** Painel do módulo: totais, pendências de configuração e ordem de implantação (Figura 1 do DOD). */
import { onMounted, ref } from 'vue'
import { api, CFG } from '@/api'
import type { Painel } from '@/api/tipos'
import Estados from '@/componentes/Estados.vue'
import Icone from '@/componentes/Icone.vue'
import Selo from '@/componentes/Selo.vue'
import { sessao, pode } from '@/estado/sessao'
import { fmtDate, fmtNumber } from '@/lib/format'

const painel = ref<Painel | null>(null)
const carregando = ref(true)
const erro = ref<string | null>(null)

async function carregar() {
  carregando.value = true
  erro.value = null
  try {
    painel.value = (await api.get<Painel>(`${CFG}/painel`)).dados
  } catch (e) {
    erro.value = (e as Error).message
  } finally {
    carregando.value = false
  }
}
onMounted(carregar)

const kpis: Array<{ chave: string; rotulo: string; para: string; cor: string }> = [
  { chave: 'contratosVigentes', rotulo: 'Contratos vigentes', para: '/contratos', cor: 'success' },
  { chave: 'contratosAtivos', rotulo: 'Contratos ativos', para: '/contratos', cor: '' },
  { chave: 'contratadas', rotulo: 'Contratadas', para: '/contratadas', cor: '' },
  { chave: 'slas', rotulo: 'SLAs', para: '/slas', cor: 'governance' },
  { chave: 'sistemasSemSla', rotulo: 'Sistemas sem SLA vigente', para: '/portfolio', cor: 'warning' },
  { chave: 'servicos', rotulo: 'Serviços', para: '/servicos', cor: '' },
  { chave: 'calendarios', rotulo: 'Calendários', para: '/calendarios', cor: '' },
  { chave: 'notificacoes', rotulo: 'Notificações', para: '/notificacoes', cor: '' },
]

const linkPendencia = (p: { recurso: string; recursoId: string | null }) =>
  p.recurso === 'contrato' ? `/contratos/${p.recursoId}` : p.recurso === 'sla' ? `/slas/${p.recursoId}` : '/portfolio'

const ordem: Array<[string, string]> = [
  ['Calendário (N10)', '/calendarios'], ['Unidade de medida (N07)', '/unidades-medida'], ['Fonte de recursos (N08)', '/fontes-recurso'],
  ['Contratada e equipe (N02)', '/contratadas'], ['Serviço (N09)', '/servicos'], ['Contrato (N01)', '/contratos'],
  ['SLA (N03)', '/slas'], ['Vínculo com sistemas (N04)', '/portfolio'],
]
</script>

<template>
  <div class="page-head">
    <div>
      <h1>Olá, {{ sessao.usuario?.nome.split(' ')[0] }}</h1>
      <p class="mid">Cadastros de apoio e parametrizações da ferramenta de gestão de demandas.</p>
    </div>
  </div>
  <Estados :carregando="carregando" :erro="erro" :linhas="6" @tentar-novamente="carregar">
    <template v-if="painel">
      <section aria-labelledby="t-indicadores">
        <h2 id="t-indicadores" class="sr-only">Indicadores</h2>
        <div class="kpis">
          <template v-for="k in kpis" :key="k.chave">
            <RouterLink v-if="painel.totais[k.chave] !== undefined" :to="k.para" class="kpi" :class="k.cor">
              <div class="valor">{{ fmtNumber(painel.totais[k.chave]) }}</div>
              <div class="rotulo">{{ k.rotulo }}</div>
            </RouterLink>
          </template>
        </div>
      </section>
      <div class="grade-2 mt-4">
        <section class="card" aria-labelledby="t-pendencias">
          <div class="card-head">
            <h2 id="t-pendencias">Pendências de configuração</h2>
            <span class="small subtle">Referência: {{ fmtDate(painel.dataReferencia) }}</span>
          </div>
          <div v-if="!painel.pendencias.length" class="vazio"><Icone nome="ok" tamanho="lg" /><p>Nenhuma pendência.</p></div>
          <ul v-else class="stack lista-limpa">
            <li v-for="(p, i) in painel.pendencias" :key="i" class="row-between">
              <span class="row">
                <Selo :tipo="p.tipo === 'SLA_EM_AVALIACAO' ? 'governance' : p.tipo === 'CONTRATO_SEM_FISCAL' ? 'danger' : 'warning'"
                      :texto="p.tipo === 'SLA_EM_AVALIACAO' ? 'Governança' : p.tipo === 'CONTRATO_SEM_FISCAL' ? 'Crítico' : 'Atenção'" />
                <span>{{ p.descricao }}</span>
              </span>
              <RouterLink :to="linkPendencia(p)" class="btn btn-ghost">Ver<Icone nome="seta" tamanho="sm" /></RouterLink>
            </li>
          </ul>
          <p v-if="!painel.portfolioDisponivel" class="alerta alerta--aviso mt-3"><Icone nome="alerta" />Portfólio indisponível: pendências de sistemas podem estar incompletas.</p>
        </section>
        <section v-if="pode('escreverCadastros')" class="card" aria-labelledby="t-ordem">
          <h2 id="t-ordem" class="mb-3">Ordem de implantação dos cadastros</h2>
          <p class="small mid">A ordem segue as dependências da Figura 1 do DOD. Notificação (N06) e Auditoria (N05) são independentes.</p>
          <ol class="stack">
            <li v-for="[nome, para] in ordem" :key="para"><RouterLink :to="para">{{ nome }}</RouterLink></li>
          </ol>
        </section>
      </div>
    </template>
  </Estados>
</template>

<style scoped>
.lista-limpa { list-style: none; margin: 0; padding: 0; }
.lista-limpa li { border-bottom: 1px solid var(--nx-color-border); padding-bottom: var(--nx-space-2); }
ol.stack { margin: 0; padding-left: var(--nx-space-5); }
</style>
