<script setup lang="ts">
import { onMounted } from 'vue'
import { CFG } from '@/api'
import type { Calendario } from '@/api/tipos'
import { useLista } from '@/composables/useLista'
import Estados from '@/componentes/Estados.vue'
import Paginacao from '@/componentes/Paginacao.vue'
import Selo from '@/componentes/Selo.vue'
import AcaoPrincipal from '@/componentes/AcaoPrincipal.vue'
import { pode } from '@/estado/sessao'
import { fmtDate, fmtNumber } from '@/lib/format'

const { filtros, pagina, carregando, erro, carregar } = useLista<Calendario>(`${CFG}/calendarios`, { termo: '', situacao: '' })
onMounted(() => carregar())
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><span class="atual">Calendários</span></nav>
      <h1>Calendários</h1>
      <p class="small mid">Fuso horário, expediente e feriados usados para contar prazos de SLA em horário comercial.</p>
    </div>
    <AcaoPrincipal v-if="pode('escreverCadastros')" rotulo="Novo calendário" para="/calendarios/novo" />
  </div>
  <section class="card">
    <div class="filtros" role="search">
      <div class="campo"><label for="c-t">Buscar</label><input id="c-t" v-model="filtros.termo" class="input" placeholder="Nome" /></div>
      <div class="campo"><label for="c-s">Situação</label>
        <select id="c-s" v-model="filtros.situacao" class="input"><option value="">Todas</option><option value="ATIVO">Ativo</option><option value="DESATIVADO">Desativado</option></select></div>
    </div>
    <Estados :carregando="carregando" :erro="erro" :vazio="!pagina.content.length" @tentar-novamente="carregar()">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Nome</th><th>Fuso</th><th class="right">Horas semanais</th><th class="right">Feriados</th><th>Vigência</th><th>Situação</th></tr></thead>
          <tbody>
            <tr v-for="c in pagina.content" :key="c.id">
              <td data-rotulo="Nome"><RouterLink :to="`/calendarios/${c.id}`">{{ c.nome }}</RouterLink><div v-if="c.descricao" class="small subtle">{{ c.descricao }}</div></td>
              <td data-rotulo="Fuso" class="mono">{{ c.fusoHorario }}</td>
              <td data-rotulo="Horas semanais" class="right num">{{ fmtNumber(c.horasSemanais) }}</td>
              <td data-rotulo="Feriados" class="right num">{{ c.feriados.length }}</td>
              <td data-rotulo="Vigência">desde {{ fmtDate(c.vigenciaInicio) }}</td>
              <td data-rotulo="Situação"><Selo :valor="c.situacao" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <Paginacao v-bind="pagina" @ir="carregar" />
    </Estados>
  </section>
</template>
