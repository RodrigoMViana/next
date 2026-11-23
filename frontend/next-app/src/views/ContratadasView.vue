<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CFG } from '@/api'
import type { Contratada } from '@/api/tipos'
import { useLista } from '@/composables/useLista'
import Estados from '@/componentes/Estados.vue'
import Paginacao from '@/componentes/Paginacao.vue'
import Selo from '@/componentes/Selo.vue'
import AcaoPrincipal from '@/componentes/AcaoPrincipal.vue'
import FormContratada from '@/componentes/FormContratada.vue'
import { pode } from '@/estado/sessao'

const router = useRouter()
const { filtros, pagina, carregando, erro, carregar } = useLista<Contratada>(`${CFG}/contratadas`, { termo: '', situacao: '' })
const novo = ref(false)
onMounted(() => carregar())
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><span class="atual">Contratadas</span></nav>
      <h1>Contratadas</h1>
    </div>
    <AcaoPrincipal v-if="pode('escreverCadastros')" rotulo="Nova contratada" @acionar="novo = true" />
  </div>
  <section class="card">
    <div class="filtros" role="search">
      <div class="campo"><label for="f-termo">Buscar</label><input id="f-termo" v-model="filtros.termo" class="input" placeholder="Razão social, nome fantasia ou CNPJ" /></div>
      <div class="campo"><label for="f-sit">Situação</label>
        <select id="f-sit" v-model="filtros.situacao" class="input"><option value="">Todas</option><option value="ATIVO">Ativo</option><option value="DESATIVADO">Desativado</option></select></div>
    </div>
    <Estados :carregando="carregando" :erro="erro" :vazio="!pagina.content.length" @tentar-novamente="carregar()">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Razão social</th><th>CNPJ</th><th class="right">Contratos ativos</th><th>Situação</th></tr></thead>
          <tbody>
            <tr v-for="c in pagina.content" :key="c.id">
              <td data-rotulo="Razão social"><RouterLink :to="`/contratadas/${c.id}`">{{ c.razaoSocial }}</RouterLink><div v-if="c.nomeFantasia" class="small subtle">{{ c.nomeFantasia }}</div></td>
              <td data-rotulo="CNPJ"><span class="mono">{{ c.cnpjFormatado }}</span> <Selo v-if="c.cnpjAlfanumerico" tipo="accent" texto="Alfanumérico" /></td>
              <td data-rotulo="Contratos ativos" class="right num">{{ c.contratosAtivos }}</td>
              <td data-rotulo="Situação"><Selo :valor="c.situacao" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <Paginacao v-bind="pagina" @ir="carregar" />
    </Estados>
  </section>
  <FormContratada v-if="novo" @fechar="novo = false" @salvo="(c) => router.push(`/contratadas/${c.id}`)" />
</template>
