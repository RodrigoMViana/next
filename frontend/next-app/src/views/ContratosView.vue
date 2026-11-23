<script setup lang="ts">
/** RF01: lista de contratos com filtros por situação, contratada e vigência, paginação e ordenação. */
import { onMounted, ref } from 'vue'
import { api, CFG } from '@/api'
import type { ContratoResumo, Contratada, Pagina } from '@/api/tipos'
import { useLista } from '@/composables/useLista'
import Estados from '@/componentes/Estados.vue'
import Paginacao from '@/componentes/Paginacao.vue'
import Selo from '@/componentes/Selo.vue'
import AcaoPrincipal from '@/componentes/AcaoPrincipal.vue'
import { pode, sessao } from '@/estado/sessao'
import { fmtDate, fmtMoney } from '@/lib/format'

const { filtros, pagina, carregando, erro, carregar, ordem, ordenar } = useLista<ContratoResumo>(`${CFG}/contratos`,
  { termo: '', situacao: '', contratadaId: '', vigenteEm: '' }, 'numero,asc')
const contratadas = ref<Contratada[]>([])

onMounted(async () => {
  carregar()
  contratadas.value = (await api.get<Pagina<Contratada>>(`${CFG}/contratadas`, { size: 100 })).dados.content
})

const seta = (c: string) => (ordem.value.startsWith(c + ',') ? (ordem.value.endsWith('desc') ? ' ↓' : ' ↑') : '')
const ariaSort = (c: string) => (ordem.value.startsWith(c + ',') ? (ordem.value.endsWith('desc') ? 'descending' : 'ascending') : 'none')
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><span class="atual">Contratos</span></nav>
      <h1>Contratos</h1>
      <p v-if="sessao.usuario?.usuarioDeFabrica" class="small mid">Exibindo apenas os contratos da sua fábrica.</p>
    </div>
    <AcaoPrincipal v-if="pode('escreverCadastros')" rotulo="Novo contrato" para="/contratos/novo" />
  </div>
  <section class="card">
    <div class="filtros" role="search">
      <div class="campo"><label for="f-termo">Buscar</label><input id="f-termo" v-model="filtros.termo" class="input" placeholder="Número ou objeto" /></div>
      <div class="campo"><label for="f-sit">Situação</label>
        <select id="f-sit" v-model="filtros.situacao" class="input"><option value="">Todas</option><option value="ATIVO">Ativo</option><option value="DESATIVADO">Desativado</option></select></div>
      <div class="campo"><label for="f-ctd">Contratada</label>
        <select id="f-ctd" v-model="filtros.contratadaId" class="input"><option value="">Todas</option><option v-for="c in contratadas" :key="c.id" :value="c.id">{{ c.razaoSocial }}</option></select></div>
      <div class="campo"><label for="f-vig">Vigente em</label><input id="f-vig" v-model="filtros.vigenteEm" type="date" class="input" /></div>
    </div>
    <Estados :carregando="carregando" :erro="erro" :vazio="!pagina.content.length" texto-vazio="Nenhum contrato encontrado com os filtros informados." @tentar-novamente="carregar()">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <caption class="sr-only">Contratos</caption>
          <thead>
            <tr>
              <th :aria-sort="ariaSort('numero')"><button type="button" @click="ordenar('numero')">Número{{ seta('numero') }}</button></th>
              <th :aria-sort="ariaSort('contratada')"><button type="button" @click="ordenar('contratada')">Contratada{{ seta('contratada') }}</button></th>
              <th :aria-sort="ariaSort('vigenciaFim')"><button type="button" @click="ordenar('vigenciaFim')">Vigência{{ seta('vigenciaFim') }}</button></th>
              <th class="right">Valor efetivo</th>
              <th>Situação</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in pagina.content" :key="c.id">
              <td data-rotulo="Número"><RouterLink :to="`/contratos/${c.id}`" class="mono">{{ c.numero }}</RouterLink><div class="small subtle">{{ c.objeto }}</div></td>
              <td data-rotulo="Contratada">{{ c.contratada.nome }}<div class="small subtle mono">{{ c.contratada.codigo }}</div></td>
              <td data-rotulo="Vigência" class="nowrap">{{ fmtDate(c.vigenciaInicio) }} a {{ fmtDate(c.vigenciaFimEfetiva) }}
                <div v-if="c.vigenciaFimEfetiva !== c.vigenciaFim" class="small subtle">original: {{ fmtDate(c.vigenciaFim) }}</div></td>
              <td data-rotulo="Valor efetivo" class="right num">{{ fmtMoney(c.valorEfetivo) }}</td>
              <td data-rotulo="Situação">
                <Selo :valor="c.situacao" />
                <Selo v-if="c.situacao === 'ATIVO'" class="mt-2" :tipo="c.vigente ? 'primary' : 'warning'" :texto="c.vigente ? 'Vigente' : 'Fora da vigência'" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Paginacao v-bind="pagina" @ir="carregar" />
    </Estados>
  </section>
</template>
