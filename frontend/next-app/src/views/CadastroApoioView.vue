<script setup lang="ts">
/** RF14: unidades de medida (N07), fontes de recursos (N08) e serviços (N09) — mesma tela, três cadastros. */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, CFG } from '@/api'
import { ProblemaError } from '@/api/cliente'
import type { Pagina, UnidadeMedida } from '@/api/tipos'
import { useLista } from '@/composables/useLista'
import Estados from '@/componentes/Estados.vue'
import Paginacao from '@/componentes/Paginacao.vue'
import Selo from '@/componentes/Selo.vue'
import Icone from '@/componentes/Icone.vue'
import Janela from '@/componentes/Janela.vue'
import Campo from '@/componentes/Campo.vue'
import AcaoPrincipal from '@/componentes/AcaoPrincipal.vue'
import HistoricoLista from '@/componentes/HistoricoLista.vue'
import MudarSituacao from '@/componentes/MudarSituacao.vue'
import { avisar } from '@/estado/avisos'
import { pode } from '@/estado/sessao'
import { rotulo } from '@/lib/format'

type Registro = Record<string, any>
const tipo = useRoute().meta.tipo as 'servicos' | 'unidades-medida' | 'fontes-recurso'
const cfg = {
  'unidades-medida': { titulo: 'Unidades de medida', singular: 'unidade de medida', chave: 'sigla', rotuloChave: 'Sigla', ajuda: 'Por exemplo, Ponto de Função (PF) e hora. O PF estimado e realizado é apurado pela Engenharia (RN16).' },
  'fontes-recurso': { titulo: 'Fontes de recursos', singular: 'fonte de recursos', chave: 'codigo', rotuloChave: 'Código', ajuda: 'Origens orçamentárias que custeiam os contratos.' },
  servicos: { titulo: 'Serviços', singular: 'serviço', chave: 'codigo', rotuloChave: 'Código', ajuda: 'Serviços monitorados por SLA, com tipo e unidade de medida (RN15).' },
}[tipo]
const caminho = `${CFG}/${tipo}`
const { filtros, pagina, carregando, erro, carregar } = useLista<Registro>(caminho, { termo: '', situacao: '', ...(tipo === 'servicos' ? { tipo: '' } : {}) })
const unidades = ref<UnidadeMedida[]>([])
const escrever = pode('escreverCadastros')

onMounted(async () => {
  carregar()
  if (tipo === 'servicos') unidades.value = (await api.get<Pagina<UnidadeMedida>>(`${CFG}/unidades-medida`, { size: 100 })).dados.content
})

const form = reactive({ aberto: false, registro: null as Registro | null, etag: '' as string | undefined, dados: {} as Registro, erros: {} as Record<string, string> })
async function abrir(r?: Registro) {
  form.erros = {}
  form.registro = r ?? null
  if (r) {
    const x = await api.get<Registro>(`${caminho}/${r.id}`)
    form.etag = x.etag
    form.dados = { [cfg.chave]: x.dados[cfg.chave], nome: x.dados.nome, descricao: x.dados.descricao ?? '', tipo: x.dados.tipo, unidadeMedidaId: x.dados.unidadeMedida?.id }
  } else form.dados = { [cfg.chave]: '', nome: '', descricao: '', tipo: 'DESENVOLVIMENTO', unidadeMedidaId: unidades.value.find((u) => u.situacao === 'ATIVO')?.id }
  form.aberto = true
}
async function salvar() {
  form.erros = {}
  try {
    if (form.registro) await api.put(`${caminho}/${form.registro.id}`, form.dados, form.etag)
    else await api.post(caminho, form.dados)
    avisar(form.registro ? 'Registro alterado.' : 'Registro cadastrado.')
    form.aberto = false
    carregar(pagina.value.page)
  } catch (e) {
    if (e instanceof ProblemaError) form.erros = { _: e.message, ...e.campos }
  }
}

const situacao = ref<Registro | null>(null)
const historico = ref<Registro | null>(null)
const colunaExtra = computed(() => tipo === 'servicos')
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><span class="atual">{{ cfg.titulo }}</span></nav>
      <h1>{{ cfg.titulo }}</h1>
      <p class="small mid">{{ cfg.ajuda }}</p>
    </div>
    <AcaoPrincipal v-if="escrever" :rotulo="`Nova ${cfg.singular}`.replace('Nova serviço', 'Novo serviço')" @acionar="abrir()" />
  </div>
  <section class="card">
    <div class="filtros" role="search">
      <div class="campo"><label for="a-t">Buscar</label><input id="a-t" v-model="filtros.termo" class="input" :placeholder="`${cfg.rotuloChave} ou nome`" /></div>
      <div v-if="tipo === 'servicos'" class="campo"><label for="a-tp">Tipo</label>
        <select id="a-tp" v-model="filtros.tipo" class="input"><option value="">Todos</option><option value="DESENVOLVIMENTO">Desenvolvimento</option><option value="MANUTENCAO">Manutenção</option><option value="SUSTENTACAO">Sustentação</option></select></div>
      <div class="campo"><label for="a-s">Situação</label>
        <select id="a-s" v-model="filtros.situacao" class="input"><option value="">Todas</option><option value="ATIVO">Ativo</option><option value="DESATIVADO">Desativado</option></select></div>
    </div>
    <Estados :carregando="carregando" :erro="erro" :vazio="!pagina.content.length" @tentar-novamente="carregar()">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>{{ cfg.rotuloChave }}</th><th>Nome</th><th v-if="colunaExtra">Tipo / unidade</th><th>Situação</th><th><span class="sr-only">Ações</span></th></tr></thead>
          <tbody>
            <tr v-for="r in pagina.content" :key="r.id">
              <td :data-rotulo="cfg.rotuloChave" class="mono">{{ r[cfg.chave] }}</td>
              <td data-rotulo="Nome">{{ r.nome }}<div v-if="r.descricao" class="small subtle">{{ r.descricao }}</div></td>
              <td v-if="colunaExtra" data-rotulo="Tipo / unidade">{{ rotulo(r.tipo) }} · {{ r.unidadeMedida?.codigo }}</td>
              <td data-rotulo="Situação"><Selo :valor="r.situacao" /><div v-if="r.justificativaSituacao" class="small subtle">{{ r.justificativaSituacao }}</div></td>
              <td data-rotulo="Ações" class="nowrap">
                <button v-if="escrever" type="button" class="btn btn-ghost" @click="abrir(r)">Editar</button>
                <button v-if="escrever" type="button" class="btn btn-ghost" @click="situacao = r">{{ r.situacao === 'ATIVO' ? 'Desativar' : 'Reativar' }}</button>
                <button type="button" class="btn btn-ghost" @click="historico = r">Histórico</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Paginacao v-bind="pagina" @ir="carregar" />
    </Estados>
  </section>

  <Janela v-if="form.aberto" :titulo="form.registro ? `Editar ${cfg.singular}` : `Cadastrar ${cfg.singular}`" @fechar="form.aberto = false">
    <p v-if="form.erros._" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ form.erros._ }}</p>
    <form class="form-grid" novalidate @submit.prevent="salvar">
      <Campo id="ap-c" :rotulo="cfg.rotuloChave" classe="col-4" :erro="form.erros[cfg.chave]" obrigatorio v-slot="{ id, descricao, invalido }">
        <input :id="id" v-model="form.dados[cfg.chave]" class="input mono" maxlength="30" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
      <Campo id="ap-n" rotulo="Nome" classe="col-8" :erro="form.erros.nome" obrigatorio v-slot="{ id, descricao, invalido }">
        <input :id="id" v-model="form.dados.nome" class="input" maxlength="200" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
      <template v-if="tipo === 'servicos'">
        <Campo id="ap-t" rotulo="Tipo" classe="col-6" :erro="form.erros.tipo" obrigatorio v-slot="{ id }">
          <select :id="id" v-model="form.dados.tipo" class="input"><option value="DESENVOLVIMENTO">Desenvolvimento</option><option value="MANUTENCAO">Manutenção</option><option value="SUSTENTACAO">Sustentação</option></select></Campo>
        <Campo id="ap-u" rotulo="Unidade de medida" classe="col-6" :erro="form.erros.unidadeMedidaId" obrigatorio v-slot="{ id }">
          <select :id="id" v-model="form.dados.unidadeMedidaId" class="input"><option v-for="u in unidades" :key="u.id" :value="u.id" :disabled="u.situacao !== 'ATIVO'">{{ u.sigla }} – {{ u.nome }}</option></select></Campo>
      </template>
      <Campo id="ap-d" rotulo="Descrição" classe="col-12" v-slot="{ id }"><textarea :id="id" v-model="form.dados.descricao" class="input" maxlength="1000" /></Campo>
      <button type="submit" class="sr-only">Salvar</button>
    </form>
    <template #acoes><button type="button" class="btn btn-secondary" @click="form.aberto = false">Cancelar</button><button type="button" class="btn btn-primary" @click="salvar">Salvar</button></template>
  </Janela>
  <MudarSituacao v-if="situacao" :caminho="`${caminho}/${situacao.id}`" :ativo="situacao.situacao === 'ATIVO'" :nome="cfg.singular" @fechar="situacao = null" @concluido="(situacao = null), carregar(pagina.page)" />
  <Janela v-if="historico" :titulo="`Histórico: ${historico[cfg.chave]}`" largo @fechar="historico = null"><HistoricoLista :caminho="`${caminho}/${historico.id}/historico`" /></Janela>
</template>
