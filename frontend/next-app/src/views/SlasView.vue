<script setup lang="ts">
import { onMounted } from 'vue'
import { CFG } from '@/api'
import type { SlaResumo } from '@/api/tipos'
import { useLista } from '@/composables/useLista'
import Estados from '@/componentes/Estados.vue'
import Paginacao from '@/componentes/Paginacao.vue'
import Selo from '@/componentes/Selo.vue'
import AcaoPrincipal from '@/componentes/AcaoPrincipal.vue'
import { pode } from '@/estado/sessao'
import { fmtDate } from '@/lib/format'

const { filtros, pagina, carregando, erro, carregar } = useLista<SlaResumo>(`${CFG}/slas`, { termo: '', situacao: '', tipo: '' })
onMounted(() => carregar())
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><span class="atual">SLA</span></nav>
      <h1>Acordos de nível de serviço</h1>
    </div>
    <AcaoPrincipal v-if="pode('escreverCadastros')" rotulo="Novo SLA" para="/slas/novo" />
  </div>
  <section class="card">
    <div class="filtros" role="search">
      <div class="campo"><label for="f-termo">Buscar</label><input id="f-termo" v-model="filtros.termo" class="input" placeholder="Código ou nome" /></div>
      <div class="campo"><label for="f-tipo">Responsável</label>
        <select id="f-tipo" v-model="filtros.tipo" class="input"><option value="">Todos</option><option value="CONTRATADA">Contratada</option><option value="EQUIPE_INTERNA">Equipe interna</option></select></div>
      <div class="campo"><label for="f-sit">Situação</label>
        <select id="f-sit" v-model="filtros.situacao" class="input"><option value="">Todas</option><option value="ATIVO">Ativo</option><option value="DESATIVADO">Desativado</option></select></div>
    </div>
    <Estados :carregando="carregando" :erro="erro" :vazio="!pagina.content.length" @tentar-novamente="carregar()">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Código</th><th>Responsável</th><th>Versão vigente</th><th>Última versão</th><th>Situação</th></tr></thead>
          <tbody>
            <tr v-for="s in pagina.content" :key="s.id">
              <td data-rotulo="Código"><RouterLink :to="`/slas/${s.id}`" class="mono">{{ s.codigo }}</RouterLink><div class="small subtle">{{ s.nome }}</div></td>
              <td data-rotulo="Responsável">{{ s.tipoResponsavel === 'CONTRATADA' ? s.contratadaNome : s.equipeInterna }}
                <div class="small subtle">{{ s.tipoResponsavel === 'CONTRATADA' ? `Contrato ${s.contrato?.codigo}` : 'Equipe interna' }}</div></td>
              <td data-rotulo="Versão vigente">{{ s.versaoVigente ? `v${s.versaoVigente} desde ${fmtDate(s.vigenteDesde)}` : '—' }}</td>
              <td data-rotulo="Última versão"><Selo :valor="s.situacaoUltimaVersao" /></td>
              <td data-rotulo="Situação"><Selo :valor="s.situacao" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <Paginacao v-bind="pagina" @ir="carregar" />
    </Estados>
  </section>
</template>
