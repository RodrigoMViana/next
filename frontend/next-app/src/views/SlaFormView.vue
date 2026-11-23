<script setup lang="ts">
/** RF09: SLA para contratada (via contrato vigente) ou equipe interna; a criação gera a versão 1 em rascunho. */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, CFG, novaChave } from '@/api'
import { ProblemaError } from '@/api/cliente'
import type { Calendario, ContratoDetalhe, ContratoResumo, Pagina, RegraSla, Servico, SistemaPortfolio, SlaDetalhe } from '@/api/tipos'
import Campo from '@/componentes/Campo.vue'
import Icone from '@/componentes/Icone.vue'
import Estados from '@/componentes/Estados.vue'
import EditorRegras from '@/componentes/EditorRegras.vue'
import { avisar } from '@/estado/avisos'

const route = useRoute()
const router = useRouter()
const id = route.params.id as string | undefined
const edicao = !!id
const chave = novaChave()
const carregando = ref(true)
const erroCarga = ref<string | null>(null)
const erros = ref<Record<string, string>>({})
const mensagem = ref<string | null>(null)
const etag = ref<string | undefined>()

const f = reactive({ codigo: '', nome: '', tipoResponsavel: 'CONTRATADA' as 'CONTRATADA' | 'EQUIPE_INTERNA', contratoId: '', equipeInterna: '', servicoIds: [] as string[], sistemaIds: [] as string[] })
const regras = ref<RegraSla[]>([])
const contratos = ref<ContratoResumo[]>([])
const contratoSel = ref<ContratoDetalhe | null>(null)
const servicos = ref<Servico[]>([])
const sistemas = ref<SistemaPortfolio[]>([])
const calendarios = ref<Calendario[]>([])

onMounted(async () => {
  try {
    const [c, s, si, ca] = await Promise.all([
      api.get<Pagina<ContratoResumo>>(`${CFG}/contratos`, { situacao: 'ATIVO', size: 100 }),
      api.get<Pagina<Servico>>(`${CFG}/servicos`, { situacao: 'ATIVO', size: 100 }),
      api.get<{ sistemas: SistemaPortfolio[] }>(`${CFG}/portfolio/sistemas`),
      api.get<Pagina<Calendario>>(`${CFG}/calendarios`, { situacao: 'ATIVO', size: 100 }),
    ])
    contratos.value = c.dados.content.filter((x) => x.vigente)
    servicos.value = s.dados.content
    sistemas.value = si.dados.sistemas
    calendarios.value = ca.dados.content
    if (edicao) {
      const r = await api.get<SlaDetalhe>(`${CFG}/slas/${id}`)
      etag.value = r.etag
      const d = r.dados
      Object.assign(f, { codigo: d.codigo, nome: d.nome, tipoResponsavel: d.tipoResponsavel, contratoId: d.contrato?.id ?? '', equipeInterna: d.equipeInterna ?? '',
        servicoIds: d.servicos.map((x) => x.id), sistemaIds: d.sistemas.map((x) => x.sistemaId) })
      if (d.contrato && !contratos.value.some((x) => x.id === d.contrato!.id)) contratos.value.push({ id: d.contrato.id, numero: d.contrato.codigo, objeto: d.contrato.nome } as ContratoResumo)
      if (d.contrato) await escolherContrato()
    }
  } catch (e) {
    erroCarga.value = (e as Error).message
  } finally {
    carregando.value = false
  }
})

async function escolherContrato() {
  contratoSel.value = f.contratoId ? (await api.get<ContratoDetalhe>(`${CFG}/contratos/${f.contratoId}`)).dados : null
  if (contratoSel.value && !edicao) f.servicoIds = f.servicoIds.filter((s) => contratoSel.value!.itens.some((i) => i.servico.id === s))
}

const servicosDisponiveis = computed(() => (f.tipoResponsavel === 'CONTRATADA' && contratoSel.value
  ? servicos.value.filter((s) => contratoSel.value!.itens.some((i) => i.servico.id === s.id)) : servicos.value))

function alternar(lista: string[], v: string) {
  const i = lista.indexOf(v)
  if (i >= 0) lista.splice(i, 1)
  else lista.push(v)
}

async function salvar() {
  erros.value = {}
  mensagem.value = null
  const corpo = { ...f, contratoId: f.tipoResponsavel === 'CONTRATADA' ? f.contratoId || null : null,
    equipeInterna: f.tipoResponsavel === 'EQUIPE_INTERNA' ? f.equipeInterna : null,
    regras: edicao ? null : regras.value.map((r) => ({ ...r, calendarioId: r.formaContagem === 'HORARIO_COMERCIAL' ? r.calendarioId : null })) }
  try {
    const r = edicao ? await api.put<SlaDetalhe>(`${CFG}/slas/${id}`, corpo, etag.value) : await api.post<SlaDetalhe>(`${CFG}/slas`, corpo, { chaveIdempotencia: chave })
    avisar(edicao ? 'SLA alterado.' : 'SLA cadastrado com a versão 1 em rascunho.')
    router.push(`/slas/${r.dados.id}`)
  } catch (e) {
    if (e instanceof ProblemaError) {
      erros.value = e.campos
      mensagem.value = e.message
    } else mensagem.value = (e as Error).message
  }
}
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><RouterLink to="/slas">SLA</RouterLink><span aria-hidden="true">›</span><span class="atual">{{ edicao ? 'Editar' : 'Novo' }}</span></nav>
      <h1>{{ edicao ? `Editar ${f.codigo}` : 'Novo SLA' }}</h1>
    </div>
  </div>
  <Estados :carregando="carregando" :erro="erroCarga" :linhas="8">
    <form novalidate @submit.prevent="salvar">
      <div v-if="mensagem" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ mensagem }}</div>
      <section class="card">
        <div class="form-grid">
          <Campo id="codigo" rotulo="Código" classe="col-4" :erro="erros.codigo" obrigatorio v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="f.codigo" class="input mono" maxlength="30" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo id="nome" rotulo="Nome" classe="col-8" :erro="erros.nome" obrigatorio v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="f.nome" class="input" maxlength="200" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <fieldset class="col-12">
            <legend>Responsável pelo atendimento</legend>
            <label class="check"><input v-model="f.tipoResponsavel" type="radio" value="CONTRATADA" :disabled="edicao" />Contratada (por meio do contrato)</label>
            <label class="check"><input v-model="f.tipoResponsavel" type="radio" value="EQUIPE_INTERNA" :disabled="edicao" />Equipe interna</label>
          </fieldset>
          <Campo v-if="f.tipoResponsavel === 'CONTRATADA'" id="contrato" rotulo="Contrato" classe="col-12" :erro="erros.contratoId" obrigatorio
                 ajuda="Somente contratos ativos e dentro da vigência efetiva (RN01)." v-slot="{ id, descricao, invalido }">
            <select :id="id" v-model="f.contratoId" class="input" :aria-describedby="descricao" :aria-invalid="invalido" @change="escolherContrato">
              <option value="" disabled>Selecione</option>
              <option v-for="c in contratos" :key="c.id" :value="c.id">{{ c.numero }} – {{ c.contratada?.nome ?? c.objeto }}</option>
            </select></Campo>
          <Campo v-else id="equipe" rotulo="Equipe interna" classe="col-12" :erro="erros.equipeInterna" obrigatorio v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="f.equipeInterna" class="input" maxlength="200" placeholder="Ex.: Central de atendimento N1" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
        </div>
      </section>
      <div class="grade-2 mt-4">
        <section class="card">
          <fieldset><legend>Serviços monitorados <span class="obrigatorio" aria-hidden="true">*</span></legend>
            <label v-for="s in servicosDisponiveis" :key="s.id" class="check"><input type="checkbox" :checked="f.servicoIds.includes(s.id)" @change="alternar(f.servicoIds, s.id)" />{{ s.codigo }} – {{ s.nome }}</label>
          </fieldset>
          <p v-if="erros.servicoIds" class="campo"><span class="erro" role="alert">{{ erros.servicoIds }}</span></p>
        </section>
        <section class="card">
          <fieldset><legend>Sistemas do Portfólio cobertos</legend>
            <label v-for="s in sistemas" :key="s.id" class="check"><input type="checkbox" :checked="f.sistemaIds.includes(s.id)" @change="alternar(f.sistemaIds, s.id)" />{{ s.sigla }} – {{ s.nome }}</label>
          </fieldset>
        </section>
      </div>
      <section v-if="!edicao" class="card mt-4">
        <h2 class="mb-2">Regras da versão 1</h2>
        <EditorRegras v-model="regras" :calendarios="calendarios" :erros="erros" />
      </section>
      <div class="row mt-4">
        <button type="submit" class="btn btn-primary">Salvar</button>
        <RouterLink :to="edicao ? `/slas/${id}` : '/slas'" class="btn btn-secondary">Cancelar</RouterLink>
      </div>
    </form>
  </Estados>
</template>
