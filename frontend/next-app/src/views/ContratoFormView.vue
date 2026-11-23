<script setup lang="ts">
/** RF02: cadastro e alteração de contrato com itens, calendário, fontes, sistemas e fiscais iniciais. */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { api, CFG, novaChave } from '@/api'
import { ProblemaError } from '@/api/cliente'
import type { Calendario, Contratada, ContratoDetalhe, FonteRecurso, Pagina, Servico, SistemaPortfolio, TipoFiscal, UsuarioCore } from '@/api/tipos'
import Campo from '@/componentes/Campo.vue'
import Icone from '@/componentes/Icone.vue'
import Estados from '@/componentes/Estados.vue'
import { avisar } from '@/estado/avisos'
import { fmtMoney, hojeIso, rotulo } from '@/lib/format'

const route = useRoute()
const router = useRouter()
const id = route.params.id as string | undefined
const edicao = !!id
const carregando = ref(true)
const erroCarga = ref<string | null>(null)
const erros = ref<Record<string, string>>({})
const mensagem = ref<string | null>(null)
const salvando = ref(false)
const etag = ref<string | undefined>()
const sujo = ref(false)
const chave = novaChave()

const f = reactive({
  numero: '', objeto: '', contratadaId: '', calendarioId: '', vigenciaInicio: '', vigenciaFim: '',
  itens: [{ servicoId: '', volume: '', valorUnitario: '' }] as Array<{ servicoId: string; volume: string | number; valorUnitario: string | number }>,
  fonteIds: [] as string[], sistemaIds: [] as string[],
  fiscais: [{ usuarioId: '', tipoFiscal: 'FISCAL_TECNICO' as TipoFiscal, inicio: hojeIso() }],
})

const contratadas = ref<Contratada[]>([])
const calendarios = ref<Calendario[]>([])
const servicos = ref<Servico[]>([])
const fontes = ref<FonteRecurso[]>([])
const sistemas = ref<SistemaPortfolio[]>([])
const usuarios = ref<UsuarioCore[]>([])

onMounted(async () => {
  try {
    const [k, c, s, fo, si, us] = await Promise.all([
      api.get<Pagina<Contratada>>(`${CFG}/contratadas`, { size: 100, situacao: 'ATIVO' }),
      api.get<Pagina<Calendario>>(`${CFG}/calendarios`, { size: 100, situacao: 'ATIVO' }),
      api.get<Pagina<Servico>>(`${CFG}/servicos`, { size: 100, situacao: 'ATIVO' }),
      api.get<Pagina<FonteRecurso>>(`${CFG}/fontes-recurso`, { size: 100, situacao: 'ATIVO' }),
      api.get<{ sistemas: SistemaPortfolio[] }>(`${CFG}/portfolio/sistemas`),
      api.get<UsuarioCore[]>(`${CFG}/usuarios`),
    ])
    contratadas.value = k.dados.content
    calendarios.value = c.dados.content
    servicos.value = s.dados.content
    fontes.value = fo.dados.content
    sistemas.value = si.dados.sistemas
    usuarios.value = us.dados.filter((u) => !u.contratadaId)
    if (edicao) {
      const r = await api.get<ContratoDetalhe>(`${CFG}/contratos/${id}`)
      etag.value = r.etag
      const d = r.dados
      Object.assign(f, { numero: d.numero, objeto: d.objeto, contratadaId: d.contratada.id, calendarioId: d.calendario.id,
        vigenciaInicio: d.vigenciaInicio, vigenciaFim: d.vigenciaFim,
        itens: d.itens.map((i) => ({ servicoId: i.servico.id, volume: i.volume, valorUnitario: i.valorUnitario })),
        fonteIds: d.fontes.map((x) => x.id), sistemaIds: d.sistemas.map((x) => x.sistemaId) })
      // inclui opções desativadas já usadas pelo contrato
      if (!contratadas.value.some((x) => x.id === d.contratada.id)) contratadas.value.push({ id: d.contratada.id, razaoSocial: d.contratada.nome } as Contratada)
    }
  } catch (e) {
    erroCarga.value = (e as Error).message
  } finally {
    carregando.value = false
    setTimeout(() => (sujo.value = false))
  }
})
watch(f, () => (sujo.value = true), { deep: true })

const totalItem = (i: { volume: string | number; valorUnitario: string | number }) => Math.round(Number(i.volume || 0) * Number(i.valorUnitario || 0) * 100) / 100
const total = computed(() => f.itens.reduce((s, i) => s + totalItem(i), 0))

function alternar(lista: string[], v: string) {
  const i = lista.indexOf(v)
  if (i >= 0) lista.splice(i, 1)
  else lista.push(v)
}

async function salvar() {
  erros.value = {}
  mensagem.value = null
  salvando.value = true
  const corpo = { ...f, itens: f.itens.map((i) => ({ servicoId: i.servicoId, volume: Number(i.volume), valorUnitario: Number(i.valorUnitario) })),
    fiscais: edicao ? null : f.fiscais }
  try {
    const r = edicao
      ? await api.put<ContratoDetalhe>(`${CFG}/contratos/${id}`, corpo, etag.value)
      : await api.post<ContratoDetalhe>(`${CFG}/contratos`, corpo, { chaveIdempotencia: chave })
    sujo.value = false
    avisar(edicao ? 'Contrato alterado.' : 'Contrato cadastrado.')
    router.push(`/contratos/${r.dados.id}`)
  } catch (e) {
    if (e instanceof ProblemaError) {
      erros.value = e.campos
      mensagem.value = e.status === 412 ? e.message : e.problema.errors?.length ? 'Corrija os campos destacados.' : e.message
    } else mensagem.value = (e as Error).message
  } finally {
    salvando.value = false
  }
}

// Confirmação antes de sair com alterações não salvas (12.11)
onBeforeRouteLeave(() => (!sujo.value || confirm('Há alterações não salvas. Deseja sair mesmo assim?')))
const antesDeSair = (e: BeforeUnloadEvent) => {
  if (sujo.value) e.preventDefault()
}
window.addEventListener('beforeunload', antesDeSair)
onBeforeUnmount(() => window.removeEventListener('beforeunload', antesDeSair))
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><RouterLink to="/contratos">Contratos</RouterLink><span aria-hidden="true">›</span><span class="atual">{{ edicao ? 'Editar' : 'Novo' }}</span></nav>
      <h1>{{ edicao ? `Editar contrato ${f.numero}` : 'Novo contrato' }}</h1>
    </div>
  </div>
  <Estados :carregando="carregando" :erro="erroCarga" :linhas="10">
    <form novalidate @submit.prevent="salvar">
      <div v-if="mensagem" class="alerta alerta--erro" role="alert"><Icone nome="alerta" /><div>{{ mensagem }}<ul v-if="Object.keys(erros).length"><li v-for="(m, k) in erros" :key="k">{{ m }}</li></ul></div></div>
      <section class="card">
        <h2 class="mb-3">Dados gerais</h2>
        <div class="form-grid">
          <Campo id="numero" rotulo="Número do contrato" classe="col-4" :erro="erros.numero" obrigatorio ajuda="Código de negócio único (RN03)." v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="f.numero" class="input mono" maxlength="30" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo id="contratada" rotulo="Contratada" classe="col-8" :erro="erros.contratadaId" obrigatorio v-slot="{ id, descricao, invalido }">
            <select :id="id" v-model="f.contratadaId" class="input" :aria-describedby="descricao" :aria-invalid="invalido">
              <option value="" disabled>Selecione</option>
              <option v-for="c in contratadas" :key="c.id" :value="c.id">{{ c.razaoSocial }}{{ c.cnpjFormatado ? ` – ${c.cnpjFormatado}` : '' }}</option>
            </select></Campo>
          <Campo id="objeto" rotulo="Objeto" classe="col-12" :erro="erros.objeto" obrigatorio v-slot="{ id, descricao, invalido }">
            <textarea :id="id" v-model="f.objeto" class="input" maxlength="1000" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo id="inicio" rotulo="Início da vigência" classe="col-4" :erro="erros.vigenciaInicio" obrigatorio v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="f.vigenciaInicio" type="date" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo id="fim" rotulo="Fim da vigência" classe="col-4" :erro="erros.vigenciaFim" obrigatorio ajuda="Não pode ser anterior ao início (RN01)." v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="f.vigenciaFim" type="date" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo id="calendario" rotulo="Calendário que a contratada segue" classe="col-4" :erro="erros.calendarioId" obrigatorio v-slot="{ id, descricao, invalido }">
            <select :id="id" v-model="f.calendarioId" class="input" :aria-describedby="descricao" :aria-invalid="invalido">
              <option value="" disabled>Selecione</option>
              <option v-for="c in calendarios" :key="c.id" :value="c.id">{{ c.nome }}</option>
            </select></Campo>
        </div>
      </section>

      <section class="card">
        <div class="card-head"><h2>Itens contratados</h2><span class="num"><span class="small mid">Valor original: </span><strong>{{ fmtMoney(total) }}</strong></span></div>
        <p v-if="erros.itens" class="alerta alerta--erro" role="alert">{{ erros.itens }}</p>
        <div v-for="(i, idx) in f.itens" :key="idx" class="editor-item">
          <Campo :id="`it-s-${idx}`" rotulo="Serviço" :erro="erros[`itens[${idx}].servicoId`]" obrigatorio v-slot="{ id, descricao, invalido }">
            <select :id="id" v-model="i.servicoId" class="input" :aria-describedby="descricao" :aria-invalid="invalido">
              <option value="" disabled>Selecione</option>
              <option v-for="s in servicos" :key="s.id" :value="s.id">{{ s.codigo }} – {{ s.nome }} ({{ s.unidadeMedida.codigo }})</option>
            </select></Campo>
          <Campo :id="`it-v-${idx}`" rotulo="Volume" :erro="erros[`itens[${idx}].volume`]" obrigatorio v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="i.volume" type="number" min="0" step="0.01" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo :id="`it-u-${idx}`" rotulo="Valor unitário (R$)" :erro="erros[`itens[${idx}].valorUnitario`]" obrigatorio v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="i.valorUnitario" type="number" min="0" step="0.01" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <div class="campo"><span class="rotulo">Valor total</span><div class="num input" aria-readonly="true">{{ fmtMoney(totalItem(i)) }}</div></div>
          <button type="button" class="btn btn-ghost" :disabled="f.itens.length === 1" :aria-label="`Remover item ${idx + 1}`" @click="f.itens.splice(idx, 1)"><Icone nome="lixeira" /></button>
        </div>
        <button type="button" class="btn btn-secondary" @click="f.itens.push({ servicoId: '', volume: '', valorUnitario: '' })"><Icone nome="mais" />Adicionar item</button>
      </section>

      <div class="grade-2 mt-4">
        <section class="card">
          <fieldset>
            <legend>Fontes de recursos <span class="obrigatorio" aria-hidden="true">*</span></legend>
            <label v-for="x in fontes" :key="x.id" class="check"><input type="checkbox" :checked="f.fonteIds.includes(x.id)" @change="alternar(f.fonteIds, x.id)" />{{ x.codigo }} – {{ x.nome }}</label>
          </fieldset>
          <p v-if="erros.fonteIds" class="campo"><span class="erro" role="alert">{{ erros.fonteIds }}</span></p>
        </section>
        <section class="card">
          <fieldset>
            <legend>Sistemas do Portfólio vinculados</legend>
            <label v-for="s in sistemas" :key="s.id" class="check"><input type="checkbox" :checked="f.sistemaIds.includes(s.id)" @change="alternar(f.sistemaIds, s.id)" />{{ s.sigla }} – {{ s.nome }}</label>
          </fieldset>
          <p v-if="erros.sistemaIds" class="campo"><span class="erro" role="alert">{{ erros.sistemaIds }}</span></p>
        </section>
      </div>

      <section v-if="!edicao" class="card mt-4">
        <h2 class="mb-2">Comissão de fiscalização</h2>
        <p class="small mid">Ao menos um fiscal com designação vigente (RN06). Depois do cadastro, as substituições ficam no detalhe do contrato.</p>
        <p v-if="erros.fiscais" class="alerta alerta--erro" role="alert">{{ erros.fiscais }}</p>
        <div v-for="(x, idx) in f.fiscais" :key="idx" class="editor-item">
          <Campo :id="`fi-u-${idx}`" rotulo="Fiscal" :erro="erros.usuarioId" obrigatorio v-slot="{ id }">
            <select :id="id" v-model="x.usuarioId" class="input"><option value="" disabled>Selecione</option><option v-for="u in usuarios" :key="u.id" :value="u.id">{{ u.nomeExibicao }}</option></select></Campo>
          <Campo :id="`fi-t-${idx}`" rotulo="Tipo" obrigatorio v-slot="{ id }">
            <select :id="id" v-model="x.tipoFiscal" class="input"><option v-for="t in ['GESTOR_CONTRATO', 'FISCAL_TECNICO', 'FISCAL_ADMINISTRATIVO', 'FISCAL_REQUISITANTE']" :key="t" :value="t">{{ rotulo(t) }}</option></select></Campo>
          <Campo :id="`fi-i-${idx}`" rotulo="Início" obrigatorio v-slot="{ id }"><input :id="id" v-model="x.inicio" type="date" class="input" /></Campo>
          <span />
          <button type="button" class="btn btn-ghost" :disabled="f.fiscais.length === 1" :aria-label="`Remover fiscal ${idx + 1}`" @click="f.fiscais.splice(idx, 1)"><Icone nome="lixeira" /></button>
        </div>
        <button type="button" class="btn btn-secondary" @click="f.fiscais.push({ usuarioId: '', tipoFiscal: 'FISCAL_ADMINISTRATIVO', inicio: hojeIso() })"><Icone nome="mais" />Adicionar fiscal</button>
      </section>

      <div class="row mt-4">
        <button type="submit" class="btn btn-primary" :disabled="salvando">{{ salvando ? 'Salvando…' : 'Salvar' }}</button>
        <RouterLink :to="edicao ? `/contratos/${id}` : '/contratos'" class="btn btn-secondary">Cancelar</RouterLink>
      </div>
    </form>
  </Estados>
</template>
