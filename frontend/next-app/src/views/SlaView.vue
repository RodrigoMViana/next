<script setup lang="ts">
/**
 * SLA: versões com vigência própria (RN14), fluxo Rascunho → Em avaliação → Vigente (RF17),
 * parecer do N2 gestor da fábrica, decisão da gestão de TI e simulador de prazo (RN12, RN13, RN22).
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, CFG } from '@/api'
import { ProblemaError } from '@/api/cliente'
import type { Calendario, Pagina, RegraSla, SimulacaoSla, SlaDetalhe, VersaoSla } from '@/api/tipos'
import Estados from '@/componentes/Estados.vue'
import Selo from '@/componentes/Selo.vue'
import Icone from '@/componentes/Icone.vue'
import Janela from '@/componentes/Janela.vue'
import Campo from '@/componentes/Campo.vue'
import EditorRegras from '@/componentes/EditorRegras.vue'
import HistoricoLista from '@/componentes/HistoricoLista.vue'
import MudarSituacao from '@/componentes/MudarSituacao.vue'
import { avisar, avisarErro } from '@/estado/avisos'
import { pode, sessao } from '@/estado/sessao'
import { fmtDate, fmtDateTime, hojeIso, isoParaLocal, localParaIso, rotulo } from '@/lib/format'

const id = useRoute().params.id as string
const base = `${CFG}/slas/${id}`
const s = ref<SlaDetalhe | null>(null)
const carregando = ref(true)
const erro = ref<string | null>(null)
const aba = ref('versoes')
const versaoHist = ref(0)
const calendarios = ref<Calendario[]>([])
const escrever = pode('escreverCadastros')
const situacaoAberta = ref(false)

async function carregar() {
  carregando.value = true
  erro.value = null
  try {
    s.value = (await api.get<SlaDetalhe>(base)).dados
    versaoHist.value++
    if (escrever && !calendarios.value.length) calendarios.value = (await api.get<Pagina<Calendario>>(`${CFG}/calendarios`, { situacao: 'ATIVO', size: 100 })).dados.content
  } catch (e) {
    erro.value = (e as Error).message
  } finally {
    carregando.value = false
  }
}
onMounted(carregar)

const vigente = computed(() => s.value?.versoes.find((v) => v.situacao === 'VIGENTE' && v.vigenciaInicio! <= hojeIso() && (!v.vigenciaFim || v.vigenciaFim >= hojeIso())))
const aberta = computed(() => s.value?.versoes.find((v) => v.situacao === 'RASCUNHO' || v.situacao === 'EM_AVALIACAO'))
const podeParecer = computed(() => pode('parecerSla') && s.value?.tipoResponsavel === 'CONTRATADA' && s.value.contratadaId === sessao.usuario?.contratadaId)

// edição de regras (versão em rascunho)
const editando = ref<VersaoSla | null>(null)
const regras = ref<RegraSla[]>([])
const errosRegras = ref<Record<string, string>>({})
function editarRegras(v: VersaoSla) {
  regras.value = v.regras.map((r) => ({ ...r, calendarioId: r.calendario?.id ?? null }))
  errosRegras.value = {}
  editando.value = v
}
async function salvarRegras() {
  errosRegras.value = {}
  try {
    await api.put(`${base}/versoes/${editando.value!.id}/regras`, { regras: regras.value.map((r) => ({ ...r, calendarioId: r.formaContagem === 'HORARIO_COMERCIAL' ? r.calendarioId : null })) }, `"${editando.value!.version}"`)
    editando.value = null
    avisar('Regras da versão salvas.')
    carregar()
  } catch (e) {
    if (e instanceof ProblemaError) errosRegras.value = { _: e.message, ...e.campos }
    else avisarErro(e)
  }
}

async function acao(fn: () => Promise<unknown>, msg: string) {
  try {
    await fn()
    avisar(msg)
    carregar()
  } catch (e) {
    avisarErro(e)
  }
}
const novaVersao = () => {
  const motivo = prompt('Motivo da alteração do SLA (nova versão):')
  if (motivo) acao(() => api.post(`${base}/versoes`, { motivoAlteracao: motivo }), 'Nova versão criada em rascunho com as regras da versão anterior.')
}
const enviar = (v: VersaoSla) => acao(() => api.post(`${base}/versoes/${v.id}/envio-avaliacao`), 'Versão enviada para avaliação da contratada.')

// parecer
const parecer = reactive({ aberto: false, favoravel: true, texto: '', erro: '' })
async function enviarParecer() {
  try {
    await api.post(`${base}/versoes/${aberta.value!.id}/parecer`, { favoravel: parecer.favoravel, texto: parecer.texto })
    parecer.aberto = false
    avisar('Parecer registrado.')
    carregar()
  } catch (e) {
    parecer.erro = e instanceof ProblemaError ? (e.campos.texto ?? e.message) : (e as Error).message
  }
}

// decisão
const decisao = reactive({ aberto: false, aprovar: true, vigenciaInicio: hojeIso(), observacao: '', erros: {} as Record<string, string> })
async function decidir() {
  decisao.erros = {}
  try {
    await api.post(`${base}/versoes/${aberta.value!.id}/decisao`, { aprovar: decisao.aprovar, vigenciaInicio: decisao.aprovar ? decisao.vigenciaInicio : null, observacao: decisao.observacao || null })
    decisao.aberto = false
    avisar(decisao.aprovar ? 'Vigência decidida. Chamados já abertos mantêm a versão anterior.' : 'Versão devolvida para rascunho.')
    carregar()
  } catch (e) {
    if (e instanceof ProblemaError) decisao.erros = { _: e.message, ...e.campos }
    else avisarErro(e)
  }
}

// simulador
const sim = reactive({ regraId: '', inicio: isoParaLocal(new Date().toISOString()), agora: isoParaLocal(new Date(Date.now() + 2 * 3600_000).toISOString()), limiar: 80,
  pausaInicio: '', pausaFim: '', pausaMotivo: '', resultado: null as SimulacaoSla | null, erro: '' })
const regrasSimulaveis = computed(() => (s.value?.versoes ?? []).flatMap((v) => v.regras.filter((r) => r.metaUnidade !== 'PERCENTUAL').map((r) => ({ v, r }))))
async function simular() {
  sim.erro = ''
  sim.resultado = null
  try {
    const pausas = sim.pausaInicio ? [{ inicio: localParaIso(sim.pausaInicio), fim: sim.pausaFim ? localParaIso(sim.pausaFim) : null, motivo: sim.pausaMotivo }] : []
    sim.resultado = (await api.post<SimulacaoSla>(`${base}/simulacoes`, { regraId: sim.regraId, inicio: localParaIso(sim.inicio), agora: localParaIso(sim.agora), limiarPercentual: sim.limiar, pausas })).dados
  } catch (e) {
    sim.erro = (e as Error).message
  }
}
const horas = (min: number) => `${Math.floor(min / 60)}h${String(min % 60).padStart(2, '0')}`
const meta = (r: RegraSla) => `${r.metaValor} ${rotulo(r.metaUnidade)}`
</script>

<template>
  <Estados :carregando="carregando" :erro="erro" :linhas="8" @tentar-novamente="carregar">
    <template v-if="s">
      <div class="page-head">
        <div>
          <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><RouterLink to="/slas">SLA</RouterLink><span aria-hidden="true">›</span><span class="atual mono">{{ s.codigo }}</span></nav>
          <h1><span class="mono">{{ s.codigo }}</span> · {{ s.nome }}</h1>
          <p class="mid">
            <template v-if="s.tipoResponsavel === 'CONTRATADA'">{{ s.contratadaNome }} · contrato <RouterLink :to="`/contratos/${s.contrato?.id}`" class="mono">{{ s.contrato?.codigo }}</RouterLink></template>
            <template v-else>Equipe interna: {{ s.equipeInterna }}</template>
          </p>
          <div class="row"><Selo :valor="s.situacao" /><Selo v-if="vigente" valor="VIGENTE" :texto="`Versão ${vigente.numero} vigente`" /><Selo v-else tipo="warning" texto="Sem versão vigente" /></div>
        </div>
        <div v-if="escrever" class="row">
          <RouterLink :to="`/slas/${s.id}/editar`" class="btn btn-secondary"><Icone nome="editar" />Editar dados</RouterLink>
          <button type="button" class="btn btn-secondary" @click="situacaoAberta = true"><Icone nome="energia" />{{ s.situacao === 'ATIVO' ? 'Desativar' : 'Reativar' }}</button>
        </div>
      </div>

      <div v-if="podeParecer && aberta?.situacao === 'EM_AVALIACAO'" class="alerta alerta--info">
        <Icone nome="escudo" /><div class="grow"><strong>Versão {{ aberta.numero }} aguardando o parecer da sua fábrica.</strong></div>
        <button type="button" class="btn btn-primary" @click="(parecer.aberto = true), (parecer.erro = '')">Registrar parecer</button>
      </div>

      <div class="abas" role="tablist">
        <button v-for="[k, t] in [['versoes', 'Versões e regras'], ['simulador', 'Simulador de prazo'], ['cobertura', 'Serviços e sistemas'], ['historico', 'Histórico']]" :key="k"
                type="button" role="tab" :aria-selected="aba === k" @click="aba = k">{{ t }}</button>
      </div>

      <section v-if="aba === 'versoes'" role="tabpanel">
        <div v-if="escrever && !aberta" class="row mb-3"><button type="button" class="btn btn-primary" @click="novaVersao"><Icone nome="mais" />Nova versão</button>
          <span class="small mid">Alterar um SLA cria nova versão, sem efeito retroativo sobre chamados abertos (RN14).</span></div>
        <article v-for="v in s.versoes" :key="v.id" class="card">
          <div class="card-head">
            <h2>Versão {{ v.numero }} <Selo :valor="v.situacao" /></h2>
            <div class="row">
              <template v-if="escrever && v.situacao === 'RASCUNHO'">
                <button type="button" class="btn btn-secondary" @click="editarRegras(v)"><Icone nome="editar" />Editar regras</button>
                <button type="button" class="btn btn-primary" @click="enviar(v)">Enviar para avaliação</button>
              </template>
              <button v-if="escrever && v.situacao === 'EM_AVALIACAO'" type="button" class="btn btn-primary" @click="(decisao.aberto = true), (decisao.erros = {})">Decidir vigência</button>
            </div>
          </div>
          <dl class="dl mb-3">
            <div><dt>Vigência</dt><dd>{{ v.vigenciaInicio ? `${fmtDate(v.vigenciaInicio)} a ${v.vigenciaFim ? fmtDate(v.vigenciaFim) : 'indeterminado'}` : '—' }}</dd></div>
            <div><dt>Motivo</dt><dd>{{ v.motivoAlteracao ?? '—' }}</dd></div>
            <div v-if="s.tipoResponsavel === 'CONTRATADA'"><dt>Parecer da contratada</dt>
              <dd><template v-if="v.parecerFavoravel !== null"><Selo :tipo="v.parecerFavoravel ? 'success' : 'danger'" :texto="v.parecerFavoravel ? 'Favorável' : 'Desfavorável'" />
                <div class="small">{{ v.parecerTexto }}</div><div class="small subtle">{{ v.parecerPorNome }} · {{ fmtDateTime(v.parecerEm) }}</div></template>
                <template v-else>{{ v.situacao === 'EM_AVALIACAO' ? 'Aguardando' : '—' }}</template></dd></div>
            <div v-if="v.decisaoPorNome"><dt>Decisão</dt><dd>{{ v.decisaoPorNome }} · {{ fmtDateTime(v.decisaoEm) }}<div class="small">{{ v.decisaoObservacao }}</div></dd></div>
          </dl>
          <div class="tabela-wrap">
            <table class="tabela responsiva">
              <thead><tr><th>Classe</th><th>Indicador</th><th>Meta</th><th>Contagem</th><th>Pausa</th></tr></thead>
              <tbody>
                <tr v-for="r in v.regras" :key="r.id">
                  <td data-rotulo="Classe">{{ r.classe }}</td><td data-rotulo="Indicador">{{ r.indicador }}</td>
                  <td data-rotulo="Meta" class="num">{{ meta(r) }}</td>
                  <td data-rotulo="Contagem">{{ rotulo(r.formaContagem) }}<div v-if="r.calendario" class="small subtle">{{ r.calendario.nome }}</div></td>
                  <td data-rotulo="Pausa">{{ r.permitePausa ? 'Permite' : 'Não' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>

      <section v-else-if="aba === 'simulador'" class="card" role="tabpanel">
        <h2 class="mb-2">Simulador de prazo</h2>
        <p class="small mid">Calcula o prazo e a situação de uma regra (em dia, em risco pelo limiar, violado), descontando pausas justificadas.</p>
        <div class="form-grid">
          <Campo id="s-r" rotulo="Regra" classe="col-12" obrigatorio v-slot="{ id }">
            <select :id="id" v-model="sim.regraId" class="input"><option value="" disabled>Selecione</option>
              <option v-for="x in regrasSimulaveis" :key="x.r.id" :value="x.r.id">v{{ x.v.numero }} · {{ x.r.classe }} · {{ x.r.indicador }} ({{ meta(x.r) }}, {{ rotulo(x.r.formaContagem) }})</option></select></Campo>
          <Campo id="s-i" rotulo="Abertura do chamado" classe="col-4" v-slot="{ id }"><input :id="id" v-model="sim.inicio" type="datetime-local" class="input" /></Campo>
          <Campo id="s-a" rotulo="Momento da consulta" classe="col-4" v-slot="{ id }"><input :id="id" v-model="sim.agora" type="datetime-local" class="input" /></Campo>
          <Campo id="s-l" rotulo="Limiar de alerta (%)" classe="col-4" v-slot="{ id }"><input :id="id" v-model.number="sim.limiar" type="number" min="1" max="99" class="input" /></Campo>
          <Campo id="s-pi" rotulo="Pausa: início (opcional)" classe="col-4" v-slot="{ id }"><input :id="id" v-model="sim.pausaInicio" type="datetime-local" class="input" /></Campo>
          <Campo id="s-pf" rotulo="Pausa: fim" classe="col-4" v-slot="{ id }"><input :id="id" v-model="sim.pausaFim" type="datetime-local" class="input" /></Campo>
          <Campo id="s-pm" rotulo="Pausa: motivo" classe="col-4" v-slot="{ id }"><input :id="id" v-model="sim.pausaMotivo" class="input" /></Campo>
        </div>
        <button type="button" class="btn btn-primary mt-3" :disabled="!sim.regraId" @click="simular">Simular</button>
        <p v-if="sim.erro" class="alerta alerta--erro mt-3" role="alert"><Icone nome="alerta" />{{ sim.erro }}</p>
        <div v-if="sim.resultado" class="kpis mt-4" aria-live="polite">
          <div class="kpi"><div class="valor">{{ fmtDateTime(sim.resultado.prazo) }}</div><div class="rotulo">Prazo</div></div>
          <div class="kpi"><div class="valor num">{{ horas(sim.resultado.minutosConsumidos) }} / {{ horas(sim.resultado.minutosMeta) }}</div><div class="rotulo">Consumido / meta</div></div>
          <div class="kpi" :class="sim.resultado.situacao === 'VIOLADO' ? 'danger' : sim.resultado.situacao === 'EM_RISCO' ? 'warning' : 'success'">
            <div class="valor num">{{ sim.resultado.percentualConsumido }}%</div><div class="rotulo">Prazo consumido</div></div>
          <div class="kpi"><div class="valor"><Selo :valor="sim.resultado.situacao" /></div><div class="rotulo">Situação</div></div>
        </div>
      </section>

      <section v-else-if="aba === 'cobertura'" class="grade-2" role="tabpanel">
        <div class="card"><h2 class="mb-2">Serviços monitorados</h2><ul><li v-for="x in s.servicos" :key="x.id"><span class="mono">{{ x.codigo }}</span> {{ x.nome }}</li></ul></div>
        <div class="card"><h2 class="mb-2">Sistemas cobertos</h2>
          <p v-if="!s.sistemas.length" class="mid">Nenhum sistema vinculado.</p>
          <ul v-else><li v-for="x in s.sistemas" :key="x.sistemaId">{{ x.sistemaNome }}</li></ul>
          <RouterLink v-if="pode('lerPortfolio')" to="/portfolio" class="btn btn-ghost">Ver Portfólio<Icone nome="seta" tamanho="sm" /></RouterLink></div>
      </section>

      <section v-else class="card" role="tabpanel"><HistoricoLista :caminho="`${base}/historico`" :recarregar="versaoHist" /></section>

      <MudarSituacao v-if="situacaoAberta" :caminho="base" :ativo="s.situacao === 'ATIVO'" nome="SLA" @fechar="situacaoAberta = false" @concluido="(situacaoAberta = false), carregar()" />

      <Janela v-if="editando" :titulo="`Regras da versão ${editando.numero}`" largo @fechar="editando = null">
        <p v-if="errosRegras._" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ errosRegras._ }}</p>
        <EditorRegras v-model="regras" :calendarios="calendarios" :erros="errosRegras" />
        <template #acoes><button type="button" class="btn btn-secondary" @click="editando = null">Cancelar</button><button type="button" class="btn btn-primary" @click="salvarRegras">Salvar regras</button></template>
      </Janela>

      <Janela v-if="parecer.aberto" :titulo="`Parecer sobre a versão ${aberta?.numero}`" @fechar="parecer.aberto = false">
        <fieldset><legend>Parecer</legend>
          <label class="check"><input v-model="parecer.favoravel" type="radio" :value="true" />Favorável</label>
          <label class="check"><input v-model="parecer.favoravel" type="radio" :value="false" />Desfavorável</label></fieldset>
        <Campo id="p-t" rotulo="Fundamentação" class="mt-3" :erro="parecer.erro" obrigatorio v-slot="{ id, descricao, invalido }">
          <textarea :id="id" v-model="parecer.texto" class="input" maxlength="2000" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
        <template #acoes><button type="button" class="btn btn-secondary" @click="parecer.aberto = false">Cancelar</button><button type="button" class="btn btn-primary" @click="enviarParecer">Registrar parecer</button></template>
      </Janela>

      <Janela v-if="decisao.aberto" :titulo="`Decisão sobre a versão ${aberta?.numero}`" @fechar="decisao.aberto = false">
        <p v-if="decisao.erros._" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ decisao.erros._ }}</p>
        <fieldset><legend>Decisão</legend>
          <label class="check"><input v-model="decisao.aprovar" type="radio" :value="true" />Aprovar e definir vigência</label>
          <label class="check"><input v-model="decisao.aprovar" type="radio" :value="false" />Devolver para rascunho</label></fieldset>
        <Campo v-if="decisao.aprovar" id="d-v" rotulo="Início da vigência" class="mt-3" :erro="decisao.erros.vigenciaInicio" obrigatorio ajuda="Hoje ou data futura; sem efeito retroativo." v-slot="{ id, descricao, invalido }">
          <input :id="id" v-model="decisao.vigenciaInicio" type="date" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
        <Campo id="d-o" :rotulo="decisao.aprovar ? 'Observação' : 'Motivo da devolução'" class="mt-3" :erro="decisao.erros.observacao" :obrigatorio="!decisao.aprovar" v-slot="{ id }">
          <textarea :id="id" v-model="decisao.observacao" class="input" maxlength="2000" /></Campo>
        <template #acoes><button type="button" class="btn btn-secondary" @click="decisao.aberto = false">Cancelar</button><button type="button" class="btn btn-primary" @click="decidir">Confirmar</button></template>
      </Janela>
    </template>
  </Estados>
</template>
