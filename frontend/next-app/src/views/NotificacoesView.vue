<script setup lang="ts">
/** RF13 / RN21 / RN22: evento, canais, perfis, modelo com variáveis e limiar de alerta de SLA. */
import { computed, onMounted, reactive, ref } from 'vue'
import { api, CFG } from '@/api'
import { ProblemaError } from '@/api/cliente'
import type { EventoCatalogo, Notificacao } from '@/api/tipos'
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
import { rotulo } from '@/lib/format'

const PERFIS = ['ADMINISTRADOR', 'GESTOR_TI', 'FACILITADOR', 'RESPONSAVEL_AREA', 'MEMBRO_COMITE', 'N1', 'N2_GESTOR', 'N2_TECNICO', 'CLIENTE']
const { filtros, pagina, carregando, erro, carregar } = useLista<Notificacao>(`${CFG}/notificacoes`, { termo: '', situacao: '' })
const catalogo = ref<EventoCatalogo[]>([])
onMounted(async () => {
  carregar()
  catalogo.value = (await api.get<EventoCatalogo[]>(`${CFG}/notificacoes/eventos`)).dados
})

const form = reactive({ aberto: false, id: '', etag: undefined as string | undefined, erros: {} as Record<string, string>,
  d: { nome: '', evento: '', canalApp: true, canalEmail: false, perfis: [] as string[], assunto: '', modelo: '', limiarPercentual: null as number | null } })
const evento = computed(() => catalogo.value.find((e) => e.evento === form.d.evento))
const previa = ref<{ assunto: string; mensagem: string } | null>(null)

async function abrir(n?: Notificacao) {
  form.erros = {}
  previa.value = null
  form.id = n?.id ?? ''
  if (n) {
    const r = await api.get<Notificacao>(`${CFG}/notificacoes/${n.id}`)
    form.etag = r.etag
    Object.assign(form.d, { nome: r.dados.nome, evento: r.dados.evento, canalApp: r.dados.canalApp, canalEmail: r.dados.canalEmail, perfis: [...r.dados.perfis],
      assunto: r.dados.assunto, modelo: r.dados.modelo, limiarPercentual: r.dados.limiarPercentual })
  } else Object.assign(form.d, { nome: '', evento: catalogo.value[0]?.evento ?? '', canalApp: true, canalEmail: false, perfis: [], assunto: '', modelo: '', limiarPercentual: null })
  form.aberto = true
}
function inserir(v: string) {
  form.d.modelo += `{{${v}}}`
}
async function preVisualizar() {
  try {
    previa.value = (await api.post<{ assunto: string; mensagem: string }>(`${CFG}/notificacoes/pre-visualizacao`, { evento: form.d.evento, assunto: form.d.assunto, modelo: form.d.modelo })).dados
  } catch (e) {
    form.erros = { _: (e as Error).message }
  }
}
async function salvar() {
  form.erros = {}
  const corpo = { ...form.d, limiarPercentual: evento.value?.exigeLimiar ? form.d.limiarPercentual : null }
  try {
    if (form.id) await api.put(`${CFG}/notificacoes/${form.id}`, corpo, form.etag)
    else await api.post(`${CFG}/notificacoes`, corpo)
    avisar('Notificação salva.')
    form.aberto = false
    carregar(pagina.value.page)
  } catch (e) {
    if (e instanceof ProblemaError) form.erros = { _: e.message, ...e.campos }
  }
}
function alternarPerfil(p: string) {
  const i = form.d.perfis.indexOf(p)
  if (i >= 0) form.d.perfis.splice(i, 1)
  else form.d.perfis.push(p)
}
const chave = (v: string) => `{{${v}}}`
const situacao = ref<Notificacao | null>(null)
const historico = ref<Notificacao | null>(null)
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><span class="atual">Notificações</span></nav>
      <h1>Notificações</h1>
      <p class="small mid">Mensagens por e-mail não exibem CPF nem conteúdo de chamados (RN21).</p>
    </div>
    <AcaoPrincipal rotulo="Nova notificação" @acionar="abrir()" />
  </div>
  <section class="card">
    <div class="filtros" role="search">
      <div class="campo"><label for="n-t">Buscar</label><input id="n-t" v-model="filtros.termo" class="input" placeholder="Nome ou evento" /></div>
      <div class="campo"><label for="n-s">Situação</label>
        <select id="n-s" v-model="filtros.situacao" class="input"><option value="">Todas</option><option value="ATIVO">Ativa</option><option value="DESATIVADO">Desativada</option></select></div>
    </div>
    <Estados :carregando="carregando" :erro="erro" :vazio="!pagina.content.length" @tentar-novamente="carregar()">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Nome</th><th>Evento</th><th>Canais</th><th>Destinatários</th><th>Situação</th><th><span class="sr-only">Ações</span></th></tr></thead>
          <tbody>
            <tr v-for="n in pagina.content" :key="n.id">
              <td data-rotulo="Nome">{{ n.nome }}</td>
              <td data-rotulo="Evento" class="mono small">{{ n.evento }}<div v-if="n.limiarPercentual" class="small subtle">limiar {{ n.limiarPercentual }}%</div></td>
              <td data-rotulo="Canais"><Selo v-if="n.canalApp" tipo="primary" texto="Ferramenta" /> <Selo v-if="n.canalEmail" tipo="accent" texto="E-mail" /></td>
              <td data-rotulo="Destinatários" class="small">{{ n.perfis.map(rotulo).join(', ') }}</td>
              <td data-rotulo="Situação"><Selo :valor="n.situacao" /></td>
              <td data-rotulo="Ações" class="nowrap">
                <button type="button" class="btn btn-ghost" @click="abrir(n)">Editar</button>
                <button type="button" class="btn btn-ghost" @click="situacao = n">{{ n.situacao === 'ATIVO' ? 'Desativar' : 'Ativar' }}</button>
                <button type="button" class="btn btn-ghost" @click="historico = n">Histórico</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Paginacao v-bind="pagina" @ir="carregar" />
    </Estados>
  </section>

  <Janela v-if="form.aberto" :titulo="form.id ? 'Editar notificação' : 'Nova notificação'" largo @fechar="form.aberto = false">
    <p v-if="form.erros._" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ form.erros._ }}</p>
    <div class="form-grid">
      <Campo id="nf-n" rotulo="Nome" classe="col-6" :erro="form.erros.nome" obrigatorio v-slot="{ id }"><input :id="id" v-model="form.d.nome" class="input" maxlength="120" /></Campo>
      <Campo id="nf-e" rotulo="Evento" classe="col-6" :erro="form.erros.evento" obrigatorio :ajuda="evento?.descricao" v-slot="{ id, descricao }">
        <select :id="id" v-model="form.d.evento" class="input" :aria-describedby="descricao">
          <option v-for="e in catalogo" :key="e.evento" :value="e.evento">{{ e.evento }}</option></select></Campo>
      <fieldset class="col-6"><legend>Canais</legend>
        <label class="check"><input v-model="form.d.canalApp" type="checkbox" />Na ferramenta</label>
        <label class="check"><input v-model="form.d.canalEmail" type="checkbox" />E-mail</label>
        <p v-if="form.erros.canalApp" class="campo"><span class="erro">{{ form.erros.canalApp }}</span></p></fieldset>
      <Campo v-if="evento?.exigeLimiar" id="nf-l" rotulo="Limiar de alerta (% do prazo consumido)" classe="col-6" :erro="form.erros.limiarPercentual" obrigatorio v-slot="{ id }">
        <input :id="id" v-model.number="form.d.limiarPercentual" type="number" min="1" max="99" class="input" /></Campo>
      <fieldset class="col-12"><legend>Perfis destinatários</legend>
        <div class="chips"><label v-for="p in PERFIS" :key="p" class="check"><input type="checkbox" :checked="form.d.perfis.includes(p)" @change="alternarPerfil(p)" />{{ rotulo(p) }}</label></div>
        <p v-if="form.erros.perfis" class="campo"><span class="erro">{{ form.erros.perfis }}</span></p></fieldset>
      <Campo id="nf-a" rotulo="Assunto" classe="col-12" :erro="form.erros.assunto" obrigatorio v-slot="{ id }"><input :id="id" v-model="form.d.assunto" class="input" maxlength="200" /></Campo>
      <Campo id="nf-m" rotulo="Modelo da mensagem" classe="col-12" :erro="form.erros.modelo" obrigatorio v-slot="{ id, descricao, invalido }">
        <textarea :id="id" v-model="form.d.modelo" class="input mono" maxlength="4000" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
      <div class="col-12 chips" aria-label="Variáveis disponíveis">
        <span class="small mid">Variáveis:</span>
        <button v-for="v in evento?.variaveis ?? []" :key="v" type="button" class="badge badge--primary" @click="inserir(v)">{{ chave(v) }}</button>
      </div>
    </div>
    <div v-if="previa" class="card mt-3"><strong>{{ previa.assunto }}</strong><pre class="codigo mt-2">{{ previa.mensagem }}</pre></div>
    <template #acoes>
      <button type="button" class="btn btn-secondary" @click="preVisualizar">Pré-visualizar</button>
      <button type="button" class="btn btn-secondary" @click="form.aberto = false">Cancelar</button>
      <button type="button" class="btn btn-primary" @click="salvar">Salvar</button>
    </template>
  </Janela>
  <MudarSituacao v-if="situacao" :caminho="`${CFG}/notificacoes/${situacao.id}`" :ativo="situacao.situacao === 'ATIVO'" nome="notificação" @fechar="situacao = null" @concluido="(situacao = null), carregar(pagina.page)" />
  <Janela v-if="historico" :titulo="`Histórico: ${historico.nome}`" largo @fechar="historico = null"><HistoricoLista :caminho="`${CFG}/notificacoes/${historico.id}/historico`" /></Janela>
</template>
