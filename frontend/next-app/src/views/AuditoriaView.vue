<script setup lang="ts">
/**
 * N05: auditoria. Trilha de acesso a dados pessoais (BFF) e registros de governança (módulo), com
 * filtros (RF11) e exportação assíncrona com link temporário (RF12). O acesso a esta tela é registrado (RN23).
 */
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { api, API, CFG, salvarArquivo } from '@/api'
import type { Exportacao, Historico, Pagina, RegistroAcesso } from '@/api/tipos'
import Estados from '@/componentes/Estados.vue'
import Paginacao from '@/componentes/Paginacao.vue'
import Selo from '@/componentes/Selo.vue'
import Icone from '@/componentes/Icone.vue'
import { avisar, avisarErro } from '@/estado/avisos'
import { fmtDateTime } from '@/lib/format'

const aba = ref<'governanca' | 'acessos' | 'exportacoes'>('governanca')
const fg = reactive({ entidade: '', acao: '', usuarioId: '', de: '', ate: '' })
const fa = reactive({ usuarioId: '', acao: '', recurso: '', de: '', ate: '', dadoPessoal: '' })
const gov = ref<Pagina<Historico> | null>(null)
const acessos = ref<Pagina<RegistroAcesso> | null>(null)
const exportacoes = ref<Exportacao[]>([])
const carregando = ref(false)
const erro = ref<string | null>(null)

const periodo = (de: string, ate: string) => ({ de: de ? new Date(de + 'T00:00:00').toISOString() : undefined, ate: ate ? new Date(ate + 'T23:59:59').toISOString() : undefined })

async function carregar(page = 0) {
  carregando.value = true
  erro.value = null
  try {
    if (aba.value === 'governanca') gov.value = (await api.get<Pagina<Historico>>(`${CFG}/auditoria/governanca`, { entidade: fg.entidade, acao: fg.acao, usuarioId: fg.usuarioId, ...periodo(fg.de, fg.ate), page, size: 20 })).dados
    else if (aba.value === 'acessos') acessos.value = (await api.get<Pagina<RegistroAcesso>>(`${API}/auditoria/acessos`, { usuarioId: fa.usuarioId, acao: fa.acao, recurso: fa.recurso, dadoPessoal: fa.dadoPessoal, ...periodo(fa.de, fa.ate), page, size: 20 })).dados
    else exportacoes.value = (await api.get<Exportacao[]>(`${API}/auditoria/exportacoes`)).dados
  } catch (e) {
    erro.value = (e as Error).message
  } finally {
    carregando.value = false
  }
}
onMounted(() => carregar())
function trocar(a: typeof aba.value) {
  aba.value = a
  carregar()
}

// exportação assíncrona: solicita, acompanha e avisa quando o arquivo estiver pronto
let relogio: ReturnType<typeof setInterval> | undefined
async function exportar(tipo: 'ACESSOS' | 'GOVERNANCA') {
  const filtros = tipo === 'ACESSOS' ? { usuarioId: fa.usuarioId, acao: fa.acao, recurso: fa.recurso, ...periodo(fa.de, fa.ate) }
    : { entidade: fg.entidade, acao: fg.acao, usuarioId: fg.usuarioId, ...periodo(fg.de, fg.ate) }
  try {
    const r = await api.post<{ id: string; mensagem: string }>(`${API}/auditoria/exportacoes`, { tipo, filtros: Object.fromEntries(Object.entries(filtros).filter(([, v]) => v)) })
    avisar('Exportação solicitada', 'sucesso', r.dados.mensagem)
    acompanhar(r.dados.id)
  } catch (e) {
    avisarErro(e)
  }
}
function acompanhar(id: string) {
  clearInterval(relogio)
  relogio = setInterval(async () => {
    const lista = (await api.get<Exportacao[]>(`${API}/auditoria/exportacoes`)).dados
    exportacoes.value = lista
    const e = lista.find((x) => x.id === id)
    if (e && e.situacao !== 'PROCESSANDO') {
      clearInterval(relogio)
      if (e.situacao === 'CONCLUIDA') avisar('Arquivo de auditoria pronto', 'sucesso', `${e.totalRegistros} registro(s). Baixe na aba Exportações.`)
      else avisar('Exportação falhou', 'erro', e.mensagemErro ?? undefined)
    }
  }, 1500)
}
onBeforeUnmount(() => clearInterval(relogio))

async function baixar(e: Exportacao) {
  try {
    const link = (await api.post<{ url: string }>(`${API}/auditoria/exportacoes/${e.id}/link`)).dados.url
    const [caminho, query] = link.split('?')
    salvarArquivo(await api.baixar(caminho!, Object.fromEntries(new URLSearchParams(query))), `auditoria-${e.tipo.toLowerCase()}-${e.criadoEm.slice(0, 10)}.csv`)
  } catch (err) {
    avisarErro(err)
  }
}

const ENTIDADES = ['contrato', 'contratada', 'contratada-membro', 'contrato-fiscal', 'sla', 'sla-versao', 'servico', 'unidade-medida', 'fonte-recurso', 'calendario', 'notificacao']
const ACOES = ['CRIACAO', 'ALTERACAO', 'DESATIVACAO', 'REATIVACAO', 'ADITIVO', 'FISCAL_SUBSTITUICAO', 'FISCAL_DESIGNACAO', 'FISCAL_ENCERRAMENTO', 'SLA_EM_AVALIACAO', 'SLA_PARECER', 'SLA_VIGENCIA', 'SLA_DEVOLVIDO', 'ALTERACAO_CONTRATUAL', 'EQUIPE_INCLUSAO']
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><span class="atual">Auditoria</span></nav>
      <h1>Auditoria</h1>
      <p class="small mid">Registros imutáveis. Esta consulta é restrita a Administrador e Gestor de TI e também é registrada na trilha (RN23, RN24).</p>
    </div>
  </div>
  <div class="abas" role="tablist">
    <button type="button" role="tab" :aria-selected="aba === 'governanca'" @click="trocar('governanca')">Governança (alterações)</button>
    <button type="button" role="tab" :aria-selected="aba === 'acessos'" @click="trocar('acessos')">Acessos (LGPD)</button>
    <button type="button" role="tab" :aria-selected="aba === 'exportacoes'" @click="trocar('exportacoes')">Exportações</button>
  </div>

  <section v-if="aba === 'governanca'" class="card" role="tabpanel">
    <div class="filtros" role="search">
      <div class="campo"><label for="g-e">Recurso</label><select id="g-e" v-model="fg.entidade" class="input"><option value="">Todos</option><option v-for="e in ENTIDADES" :key="e">{{ e }}</option></select></div>
      <div class="campo"><label for="g-a">Ação</label><select id="g-a" v-model="fg.acao" class="input"><option value="">Todas</option><option v-for="a in ACOES" :key="a">{{ a }}</option></select></div>
      <div class="campo"><label for="g-de">De</label><input id="g-de" v-model="fg.de" type="date" class="input" /></div>
      <div class="campo"><label for="g-ate">Até</label><input id="g-ate" v-model="fg.ate" type="date" class="input" /></div>
      <div class="row"><button type="button" class="btn btn-primary" @click="carregar()"><Icone nome="busca" />Filtrar</button>
        <button type="button" class="btn btn-secondary" @click="exportar('GOVERNANCA')"><Icone nome="baixar" />Exportar</button></div>
    </div>
    <Estados :carregando="carregando" :erro="erro" :vazio="!gov?.content.length" @tentar-novamente="carregar()">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Quando</th><th>Quem</th><th>Recurso</th><th>Ação</th><th>Detalhe</th></tr></thead>
          <tbody>
            <tr v-for="h in gov?.content" :key="h.id">
              <td data-rotulo="Quando" class="nowrap">{{ fmtDateTime(h.ocorridoEm) }}</td>
              <td data-rotulo="Quem">{{ h.usuarioNome }}</td>
              <td data-rotulo="Recurso" class="small">{{ h.entidade }}<div class="mono subtle">{{ h.entidadeId.slice(0, 8) }}</div></td>
              <td data-rotulo="Ação"><span class="badge" :class="h.acao.startsWith('SLA') || h.acao.startsWith('FISCAL') ? 'badge--governance' : 'badge--primary'">{{ h.acao }}</span></td>
              <td data-rotulo="Detalhe" class="small"><template v-if="h.campo">{{ h.campo }}: <span class="diff-antes">{{ h.valorAnterior ?? '(vazio)' }}</span> → <span class="diff-depois">{{ h.valorNovo ?? '(vazio)' }}</span></template>
                <template v-else>{{ h.valorNovo }}</template><div v-if="h.justificativa" class="subtle">Justificativa: {{ h.justificativa }}</div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <Paginacao v-if="gov" v-bind="gov" @ir="carregar" />
    </Estados>
  </section>

  <section v-else-if="aba === 'acessos'" class="card" role="tabpanel">
    <div class="filtros" role="search">
      <div class="campo"><label for="a-a">Ação</label><select id="a-a" v-model="fa.acao" class="input"><option value="">Todas</option><option v-for="a in ['LOGIN', 'LOGOUT', 'LEITURA', 'ESCRITA', 'ALTERACAO', 'REMOCAO', 'EXPORTACAO', 'DOWNLOAD']" :key="a">{{ a }}</option></select></div>
      <div class="campo"><label for="a-p">Dado pessoal</label><select id="a-p" v-model="fa.dadoPessoal" class="input"><option value="">Todos</option><option value="true">Somente rotas com dados pessoais</option><option value="false">Sem dados pessoais</option></select></div>
      <div class="campo"><label for="a-de">De</label><input id="a-de" v-model="fa.de" type="date" class="input" /></div>
      <div class="campo"><label for="a-ate">Até</label><input id="a-ate" v-model="fa.ate" type="date" class="input" /></div>
      <div class="row"><button type="button" class="btn btn-primary" @click="carregar()"><Icone nome="busca" />Filtrar</button>
        <button type="button" class="btn btn-secondary" @click="exportar('ACESSOS')"><Icone nome="baixar" />Exportar</button></div>
    </div>
    <Estados :carregando="carregando" :erro="erro" :vazio="!acessos?.content.length" @tentar-novamente="carregar()">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Quando</th><th>Usuário</th><th>Ação</th><th>Rota</th><th>Status</th><th>IP</th></tr></thead>
          <tbody>
            <tr v-for="r in acessos?.content" :key="r.id">
              <td data-rotulo="Quando" class="nowrap">{{ fmtDateTime(r.ocorridoEm) }}</td>
              <td data-rotulo="Usuário">{{ r.usuarioNome ?? 'anônimo' }}<div class="small subtle">{{ r.perfis }}</div></td>
              <td data-rotulo="Ação">{{ r.acao }} <Selo v-if="r.dadoPessoal" tipo="governance" texto="Dado pessoal" /></td>
              <td data-rotulo="Rota" class="mono small">{{ r.metodo }} {{ r.rota }}</td>
              <td data-rotulo="Status"><Selo :tipo="r.status >= 400 ? 'danger' : 'success'" :texto="String(r.status)" /></td>
              <td data-rotulo="IP" class="small mono">{{ r.ip }}<div class="subtle">{{ r.correlationId }}</div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <Paginacao v-if="acessos" v-bind="acessos" @ir="carregar" />
    </Estados>
  </section>

  <section v-else class="card" role="tabpanel">
    <div class="card-head"><h2>Minhas exportações</h2><button type="button" class="btn btn-secondary" @click="carregar()"><Icone nome="atualizar" />Atualizar</button></div>
    <p class="small mid">O link de download é temporário (24 h) e só funciona para quem solicitou.</p>
    <Estados :carregando="carregando" :erro="erro" :vazio="!exportacoes.length" texto-vazio="Nenhuma exportação solicitada." @tentar-novamente="carregar()">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Solicitada em</th><th>Tipo</th><th>Situação</th><th class="right">Registros</th><th>Expira em</th><th><span class="sr-only">Ações</span></th></tr></thead>
          <tbody>
            <tr v-for="e in exportacoes" :key="e.id">
              <td data-rotulo="Solicitada em">{{ fmtDateTime(e.criadoEm) }}</td>
              <td data-rotulo="Tipo">{{ e.tipo === 'ACESSOS' ? 'Acessos (LGPD)' : 'Governança' }}<div v-if="e.filtros" class="small subtle mono">{{ e.filtros }}</div></td>
              <td data-rotulo="Situação"><Selo :valor="e.situacao" /></td>
              <td data-rotulo="Registros" class="right num">{{ e.totalRegistros ?? '—' }}</td>
              <td data-rotulo="Expira em">{{ fmtDateTime(e.expiraEm) }}</td>
              <td data-rotulo="Ações"><button v-if="e.situacao === 'CONCLUIDA'" type="button" class="btn btn-ghost" @click="baixar(e)"><Icone nome="baixar" tamanho="sm" />Baixar CSV</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </Estados>
  </section>
</template>
