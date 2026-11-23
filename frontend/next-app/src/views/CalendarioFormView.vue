<script setup lang="ts">
/** RF15 / RN17 / RN18: expediente por dia (vários intervalos), feriados com recorrência anual e simulação. */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, CFG } from '@/api'
import { ProblemaError } from '@/api/cliente'
import type { Calendario, Expediente, Feriado } from '@/api/tipos'
import Campo from '@/componentes/Campo.vue'
import Icone from '@/componentes/Icone.vue'
import Selo from '@/componentes/Selo.vue'
import Estados from '@/componentes/Estados.vue'
import HistoricoLista from '@/componentes/HistoricoLista.vue'
import MudarSituacao from '@/componentes/MudarSituacao.vue'
import { avisar } from '@/estado/avisos'
import { pode } from '@/estado/sessao'
import { DIAS_SEMANA, fmtDate, fmtDateTime, hojeIso, isoParaLocal, localParaIso, rotulo } from '@/lib/format'

const route = useRoute()
const router = useRouter()
const id = route.params.id as string | undefined
const edicao = !!id
const escrever = pode('escreverCadastros')
const somenteLeitura = !escrever
const carregando = ref(edicao)
const erroCarga = ref<string | null>(null)
const etag = ref<string | undefined>()
const situacao = ref<'ATIVO' | 'DESATIVADO'>('ATIVO')
const erros = ref<Record<string, string>>({})
const mensagem = ref<string | null>(null)
const aba = ref('cadastro')
const mudarSituacao = ref(false)
const versaoHist = ref(0)

const fusos = ['America/Sao_Paulo', 'America/Manaus', 'America/Belem', 'America/Fortaleza', 'America/Recife', 'America/Bahia', 'America/Cuiaba',
  'America/Campo_Grande', 'America/Porto_Velho', 'America/Boa_Vista', 'America/Rio_Branco', 'America/Noronha', 'America/Araguaina', 'America/Maceio', 'America/Santarem']
const f = reactive({ nome: '', descricao: '', fusoHorario: 'America/Sao_Paulo', vigenciaInicio: hojeIso(), expedientes: [] as Expediente[], feriados: [] as Feriado[] })

onMounted(async () => {
  if (!edicao) {
    for (let d = 1; d <= 5; d++) f.expedientes.push({ diaSemana: d, horaInicio: '08:00', horaFim: '12:00' }, { diaSemana: d, horaInicio: '14:00', horaFim: '18:00' })
    return
  }
  try {
    const r = await api.get<Calendario>(`${CFG}/calendarios/${id}`)
    etag.value = r.etag
    situacao.value = r.dados.situacao
    Object.assign(f, { nome: r.dados.nome, descricao: r.dados.descricao ?? '', fusoHorario: r.dados.fusoHorario, vigenciaInicio: r.dados.vigenciaInicio > hojeIso() ? r.dados.vigenciaInicio : hojeIso(),
      expedientes: r.dados.expedientes.map((e) => ({ ...e, horaInicio: e.horaInicio.slice(0, 5), horaFim: e.horaFim.slice(0, 5) })), feriados: r.dados.feriados })
    if (!fusos.includes(f.fusoHorario)) fusos.push(f.fusoHorario)
  } catch (e) {
    erroCarga.value = (e as Error).message
  } finally {
    carregando.value = false
  }
})

const porDia = computed(() => [1, 2, 3, 4, 5, 6, 7].map((d) => ({ dia: d, intervalos: f.expedientes.filter((e) => e.diaSemana === d) })))
const horasSemana = computed(() => f.expedientes.reduce((s, e) => s + (Number(e.horaFim.slice(0, 2)) * 60 + Number(e.horaFim.slice(3, 5)) - Number(e.horaInicio.slice(0, 2)) * 60 - Number(e.horaInicio.slice(3, 5))), 0) / 60)
const remover = (e: Expediente) => f.expedientes.splice(f.expedientes.indexOf(e), 1)
const novoFeriado = reactive<Feriado>({ data: '', descricao: '', abrangencia: 'NACIONAL', recorrenteAnual: true })
function adicionarFeriado() {
  if (!novoFeriado.data || !novoFeriado.descricao) return
  f.feriados.push({ ...novoFeriado })
  f.feriados.sort((a, b) => a.data.localeCompare(b.data))
  Object.assign(novoFeriado, { data: '', descricao: '' })
}

async function salvar() {
  erros.value = {}
  mensagem.value = null
  try {
    const r = edicao ? await api.put<Calendario>(`${CFG}/calendarios/${id}`, f, etag.value) : await api.post<Calendario>(`${CFG}/calendarios`, f)
    etag.value = r.etag
    avisar(edicao ? 'Calendário alterado. Vale a partir da vigência informada e não recalcula chamados em andamento.' : 'Calendário cadastrado.')
    versaoHist.value++
    if (!edicao) router.push(`/calendarios/${r.dados.id}`)
  } catch (e) {
    if (e instanceof ProblemaError) {
      erros.value = e.campos
      mensagem.value = e.message
    }
  }
}

// simulação
const sim = reactive({ inicio: isoParaLocal(new Date().toISOString()), meta: 4, unidade: 'HORAS', forma: 'HORARIO_COMERCIAL', resultado: null as null | { prazo: string; minutosUteis: number }, erro: '' })
async function simular() {
  sim.erro = ''
  try {
    sim.resultado = (await api.post<{ prazo: string; minutosUteis: number }>(`${CFG}/calendarios/${id}/simulacoes`,
      { inicio: localParaIso(sim.inicio), meta: sim.meta, unidade: sim.unidade, formaContagem: sim.forma })).dados
  } catch (e) {
    sim.erro = (e as Error).message
    sim.resultado = null
  }
}
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><RouterLink to="/calendarios">Calendários</RouterLink><span aria-hidden="true">›</span><span class="atual">{{ edicao ? f.nome : 'Novo' }}</span></nav>
      <h1>{{ edicao ? f.nome : 'Novo calendário' }} <Selo v-if="edicao" :valor="situacao" /></h1>
    </div>
    <button v-if="edicao && escrever" type="button" class="btn btn-secondary" @click="mudarSituacao = true"><Icone nome="energia" />{{ situacao === 'ATIVO' ? 'Desativar' : 'Reativar' }}</button>
  </div>
  <Estados :carregando="carregando" :erro="erroCarga" :linhas="8">
    <div v-if="edicao" class="abas" role="tablist">
      <button v-for="[k, t] in [['cadastro', 'Cadastro'], ['simulacao', 'Simular prazo'], ['historico', 'Histórico']]" :key="k" type="button" role="tab" :aria-selected="aba === k" @click="aba = k">{{ t }}</button>
    </div>
    <form v-if="aba === 'cadastro'" novalidate @submit.prevent="salvar">
      <div v-if="mensagem" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ mensagem }}</div>
      <fieldset :disabled="somenteLeitura" class="sem-borda">
        <section class="card">
          <div class="form-grid">
            <Campo id="c-n" rotulo="Nome" classe="col-6" :erro="erros.nome" obrigatorio v-slot="{ id, descricao, invalido }">
              <input :id="id" v-model="f.nome" class="input" maxlength="120" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
            <Campo id="c-f" rotulo="Fuso horário" classe="col-3" :erro="erros.fusoHorario" obrigatorio v-slot="{ id }">
              <select :id="id" v-model="f.fusoHorario" class="input"><option v-for="z in fusos" :key="z" :value="z">{{ z }}</option></select></Campo>
            <Campo id="c-v" rotulo="Vale a partir de" classe="col-3" :erro="erros.vigenciaInicio" obrigatorio
                   :ajuda="edicao ? 'A alteração não recalcula chamados em andamento (RN18).' : undefined" v-slot="{ id, descricao, invalido }">
              <input :id="id" v-model="f.vigenciaInicio" type="date" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
            <Campo id="c-d" rotulo="Descrição" classe="col-12" v-slot="{ id }"><input :id="id" v-model="f.descricao" class="input" maxlength="500" /></Campo>
          </div>
        </section>
        <section class="card">
          <div class="card-head"><h2>Expediente</h2><span class="small mid num">{{ horasSemana.toLocaleString('pt-BR') }} h por semana</span></div>
          <p v-if="erros.expedientes" class="alerta alerta--erro" role="alert">{{ erros.expedientes }}</p>
          <div class="expediente">
            <div v-for="d in porDia" :key="d.dia" class="expediente-dia">
              <span class="dia">{{ DIAS_SEMANA[d.dia] }}</span>
              <span v-if="!d.intervalos.length" class="subtle small">sem expediente</span>
              <span v-for="(e, i) in d.intervalos" :key="i" class="row">
                <label class="sr-only" :for="`e-${d.dia}-${i}-i`">Início</label><input :id="`e-${d.dia}-${i}-i`" v-model="e.horaInicio" type="time" class="input hora" />
                <span aria-hidden="true">–</span>
                <label class="sr-only" :for="`e-${d.dia}-${i}-f`">Fim</label><input :id="`e-${d.dia}-${i}-f`" v-model="e.horaFim" type="time" class="input hora" />
                <button v-if="escrever" type="button" class="btn-icon" :aria-label="`Remover intervalo de ${DIAS_SEMANA[d.dia]}`" @click="remover(e)"><Icone nome="fechar" tamanho="sm" /></button>
              </span>
              <button v-if="escrever" type="button" class="btn btn-ghost" @click="f.expedientes.push({ diaSemana: d.dia, horaInicio: '08:00', horaFim: '12:00' })"><Icone nome="mais" tamanho="sm" />Intervalo</button>
            </div>
          </div>
        </section>
        <section class="card">
          <h2 class="mb-3">Feriados ({{ f.feriados.length }})</h2>
          <p v-if="erros.feriados" class="alerta alerta--erro" role="alert">{{ erros.feriados }}</p>
          <div v-if="escrever" class="editor-item">
            <div class="campo"><label for="nf-d">Data</label><input id="nf-d" v-model="novoFeriado.data" type="date" class="input" /></div>
            <div class="campo"><label for="nf-n">Descrição</label><input id="nf-n" v-model="novoFeriado.descricao" class="input" /></div>
            <div class="campo"><label for="nf-a">Abrangência</label>
              <select id="nf-a" v-model="novoFeriado.abrangencia" class="input"><option v-for="a in ['NACIONAL', 'ESTADUAL', 'MUNICIPAL', 'PONTO_FACULTATIVO']" :key="a" :value="a">{{ rotulo(a) }}</option></select></div>
            <label class="check"><input v-model="novoFeriado.recorrenteAnual" type="checkbox" />Repete todo ano</label>
            <button type="button" class="btn btn-secondary" @click="adicionarFeriado"><Icone nome="mais" />Incluir</button>
          </div>
          <div class="tabela-wrap">
            <table class="tabela responsiva">
              <thead><tr><th>Data</th><th>Descrição</th><th>Abrangência</th><th>Recorrência</th><th v-if="escrever"><span class="sr-only">Ações</span></th></tr></thead>
              <tbody>
                <tr v-for="(x, i) in f.feriados" :key="i">
                  <td data-rotulo="Data">{{ x.recorrenteAnual ? fmtDate(x.data).slice(0, 5) : fmtDate(x.data) }}</td>
                  <td data-rotulo="Descrição">{{ x.descricao }}</td>
                  <td data-rotulo="Abrangência">{{ rotulo(x.abrangencia) }}</td>
                  <td data-rotulo="Recorrência">{{ x.recorrenteAnual ? 'Anual' : 'Somente nesta data' }}</td>
                  <td v-if="escrever" data-rotulo="Ações"><button type="button" class="btn btn-ghost" @click="f.feriados.splice(i, 1)">Remover</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </fieldset>
      <div v-if="escrever" class="row mt-4">
        <button type="submit" class="btn btn-primary">Salvar</button>
        <RouterLink to="/calendarios" class="btn btn-secondary">Voltar</RouterLink>
      </div>
    </form>
    <section v-else-if="aba === 'simulacao'" class="card">
      <h2 class="mb-2">Simular prazo neste calendário</h2>
      <div class="form-grid">
        <Campo id="s-i" rotulo="Início" classe="col-4" v-slot="{ id }"><input :id="id" v-model="sim.inicio" type="datetime-local" class="input" /></Campo>
        <Campo id="s-m" rotulo="Meta" classe="col-2" v-slot="{ id }"><input :id="id" v-model.number="sim.meta" type="number" min="0.01" step="0.01" class="input" /></Campo>
        <Campo id="s-u" rotulo="Unidade" classe="col-3" v-slot="{ id }"><select :id="id" v-model="sim.unidade" class="input"><option value="MINUTOS">Minutos</option><option value="HORAS">Horas</option><option value="DIAS_UTEIS">Dias úteis</option><option value="DIAS_CORRIDOS">Dias corridos</option></select></Campo>
        <Campo id="s-f" rotulo="Contagem" classe="col-3" v-slot="{ id }"><select :id="id" v-model="sim.forma" class="input"><option value="HORARIO_COMERCIAL">Horário comercial</option><option value="CORRIDO_24X7">Corrido (24x7)</option></select></Campo>
      </div>
      <button type="button" class="btn btn-primary mt-3" @click="simular">Calcular</button>
      <p v-if="sim.erro" class="alerta alerta--erro mt-3" role="alert">{{ sim.erro }}</p>
      <div v-if="sim.resultado" class="kpis mt-4" aria-live="polite">
        <div class="kpi"><div class="valor">{{ fmtDateTime(sim.resultado.prazo) }}</div><div class="rotulo">Prazo</div></div>
        <div class="kpi"><div class="valor num">{{ Math.floor(sim.resultado.minutosUteis / 60) }}h{{ String(sim.resultado.minutosUteis % 60).padStart(2, '0') }}</div><div class="rotulo">Tempo contado</div></div>
      </div>
    </section>
    <section v-else class="card"><HistoricoLista :caminho="`${CFG}/calendarios/${id}/historico`" :recarregar="versaoHist" /></section>
  </Estados>
  <MudarSituacao v-if="mudarSituacao" :caminho="`${CFG}/calendarios/${id}`" :ativo="situacao === 'ATIVO'" nome="calendário"
                 @fechar="mudarSituacao = false" @concluido="(mudarSituacao = false), (situacao = situacao === 'ATIVO' ? 'DESATIVADO' : 'ATIVO'), versaoHist++" />
</template>

<style scoped>
.sem-borda { border: 0; padding: 0; margin: 0; min-width: 0; }
.input.hora { width: 7.5rem; }
</style>
