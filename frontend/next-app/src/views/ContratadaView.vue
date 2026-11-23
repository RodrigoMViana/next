<script setup lang="ts">
/** Contratada: dados, equipe (RF07), alterações contratuais com anexo (RF08) e histórico (RF16). */
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, CFG, salvarArquivo } from '@/api'
import { ProblemaError } from '@/api/cliente'
import type { AlteracaoContratual, Contratada, Membro, UsuarioCore } from '@/api/tipos'
import Estados from '@/componentes/Estados.vue'
import Selo from '@/componentes/Selo.vue'
import Icone from '@/componentes/Icone.vue'
import Janela from '@/componentes/Janela.vue'
import Campo from '@/componentes/Campo.vue'
import HistoricoLista from '@/componentes/HistoricoLista.vue'
import MudarSituacao from '@/componentes/MudarSituacao.vue'
import FormContratada from '@/componentes/FormContratada.vue'
import { avisar, avisarErro } from '@/estado/avisos'
import { pode } from '@/estado/sessao'
import { fmtDate, fmtDateTime, fmtNumber, hojeIso, rotulo } from '@/lib/format'

const id = useRoute().params.id as string
const base = `${CFG}/contratadas/${id}`
const c = ref<Contratada | null>(null)
const etag = ref<string | undefined>()
const equipe = ref<Membro[]>([])
const alteracoes = ref<AlteracaoContratual[]>([])
const carregando = ref(true)
const erro = ref<string | null>(null)
const aba = ref('equipe')
const versaoHist = ref(0)
const escrever = pode('escreverCadastros')
const editar = ref(false)
const situacao = ref(false)

async function carregar() {
  carregando.value = true
  erro.value = null
  try {
    const r = await api.get<Contratada>(base)
    c.value = r.dados
    etag.value = r.etag
    const [e, a] = await Promise.all([api.get<Membro[]>(`${base}/equipe`), api.get<AlteracaoContratual[]>(`${base}/alteracoes`)])
    equipe.value = e.dados
    alteracoes.value = a.dados
    versaoHist.value++
  } catch (e) {
    erro.value = (e as Error).message
  } finally {
    carregando.value = false
  }
}
onMounted(carregar)

// equipe
const usuarios = ref<UsuarioCore[]>([])
const membroAberto = ref(false)
const membro = reactive({ usuarioId: '', papel: 'N2_TECNICO', inicio: hojeIso(), fim: '' })
const errosMembro = ref<Record<string, string>>({})
async function abrirMembro() {
  usuarios.value = (await api.get<UsuarioCore[]>(`${CFG}/usuarios`)).dados.filter((u) => u.perfis.some((p) => p.startsWith('N2')))
  Object.assign(membro, { usuarioId: '', papel: 'N2_TECNICO', inicio: hojeIso(), fim: '' })
  errosMembro.value = {}
  membroAberto.value = true
}
async function salvarMembro() {
  try {
    await api.post(`${base}/equipe`, { ...membro, fim: membro.fim || null })
    membroAberto.value = false
    avisar('Membro incluído na equipe. MFA é obrigatório para usuários de fábrica.')
    carregar()
  } catch (e) {
    if (e instanceof ProblemaError) errosMembro.value = { _: e.message, ...e.campos }
    else avisarErro(e)
  }
}
async function encerrarMembro(m: Membro) {
  const fim = prompt(`Data de saída de ${m.nomeExibicao} (aaaa-mm-dd):`, hojeIso())
  if (!fim) return
  try {
    await api.post(`${base}/equipe/${m.id}/encerramento`, { fim })
    avisar('Período encerrado.')
    carregar()
  } catch (e) {
    avisarErro(e)
  }
}

// alterações contratuais
const alteracaoAberta = ref(false)
const alt = reactive({ dataAlteracao: hojeIso(), tipo: 'ALTERACAO_SOCIETARIA', descricao: '' })
const arquivo = ref<File | null>(null)
const errosAlt = ref<Record<string, string>>({})
async function salvarAlteracao() {
  errosAlt.value = {}
  if (arquivo.value && arquivo.value.size > 5 * 1024 * 1024) {
    errosAlt.value = { anexo: 'O anexo deve ter no máximo 5 MB.' }
    return
  }
  const fd = new FormData()
  fd.set('dataAlteracao', alt.dataAlteracao)
  fd.set('tipo', alt.tipo)
  fd.set('descricao', alt.descricao)
  if (arquivo.value) fd.set('anexo', arquivo.value)
  try {
    await api.enviarArquivo(`${base}/alteracoes`, fd)
    alteracaoAberta.value = false
    avisar('Alteração contratual registrada.')
    carregar()
  } catch (e) {
    if (e instanceof ProblemaError) errosAlt.value = { _: e.message, ...e.campos }
    else avisarErro(e)
  }
}
async function baixar(a: AlteracaoContratual) {
  try {
    salvarArquivo(await api.baixar(`${base}/alteracoes/${a.id}/anexo`), a.anexoNome ?? 'anexo')
  } catch (e) {
    avisarErro(e)
  }
}
const TIPOS_ALTERACAO = ['ALTERACAO_SOCIETARIA', 'ALTERACAO_RAZAO_SOCIAL', 'ALTERACAO_ENDERECO', 'ALTERACAO_REPRESENTANTE', 'OUTRA']
const rotuloTipo = (t: string) => t.replace('ALTERACAO_', 'Alteração ').replace('_', ' ').toLowerCase().replace(/^./, (x) => x.toUpperCase())
</script>

<template>
  <Estados :carregando="carregando" :erro="erro" :linhas="8" @tentar-novamente="carregar">
    <template v-if="c">
      <div class="page-head">
        <div>
          <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><RouterLink to="/contratadas">Contratadas</RouterLink><span aria-hidden="true">›</span><span class="atual">{{ c.razaoSocial }}</span></nav>
          <h1>{{ c.razaoSocial }}</h1>
          <p class="mid"><span class="mono">{{ c.cnpjFormatado }}</span><span v-if="c.nomeFantasia"> · {{ c.nomeFantasia }}</span></p>
          <div class="row"><Selo :valor="c.situacao" /><Selo v-if="c.cnpjAlfanumerico" tipo="accent" texto="CNPJ alfanumérico" /><span class="small mid">{{ c.contratosAtivos }} contrato(s) ativo(s)</span></div>
        </div>
        <div v-if="escrever" class="row">
          <button type="button" class="btn btn-secondary" @click="editar = true"><Icone nome="editar" />Editar</button>
          <button type="button" class="btn btn-secondary" @click="situacao = true"><Icone nome="energia" />{{ c.situacao === 'ATIVO' ? 'Desativar' : 'Reativar' }}</button>
        </div>
      </div>
      <p v-if="c.justificativaSituacao" class="alerta alerta--info"><Icone nome="info" />Justificativa da situação: {{ c.justificativaSituacao }}</p>
      <div class="abas" role="tablist">
        <button type="button" role="tab" :aria-selected="aba === 'equipe'" @click="aba = 'equipe'">Equipe ({{ equipe.length }})</button>
        <button type="button" role="tab" :aria-selected="aba === 'alteracoes'" @click="aba = 'alteracoes'">Alterações contratuais ({{ alteracoes.length }})</button>
        <button type="button" role="tab" :aria-selected="aba === 'historico'" @click="aba = 'historico'">Histórico</button>
      </div>

      <section v-if="aba === 'equipe'" class="card" role="tabpanel">
        <div class="card-head"><h2>Equipe da contratada</h2>
          <button v-if="escrever && c.situacao === 'ATIVO'" type="button" class="btn btn-primary" @click="abrirMembro"><Icone nome="mais" />Incluir membro</button></div>
        <p class="small mid">Usuários do Core com papel N2 gestor ou N2+ técnico. O módulo guarda só o identificador e o nome de exibição (RN10). MFA obrigatório (RN09).</p>
        <div v-if="!equipe.length" class="vazio"><p>Nenhum membro vinculado.</p></div>
        <div v-else class="tabela-wrap">
          <table class="tabela responsiva">
            <thead><tr><th>Nome</th><th>Papel</th><th>Período</th><th>Situação</th><th v-if="escrever"><span class="sr-only">Ações</span></th></tr></thead>
            <tbody>
              <tr v-for="m in equipe" :key="m.id">
                <td data-rotulo="Nome">{{ m.nomeExibicao }}</td>
                <td data-rotulo="Papel">{{ rotulo(m.papel) }}</td>
                <td data-rotulo="Período">{{ fmtDate(m.inicio) }} a {{ m.fim ? fmtDate(m.fim) : 'sem término' }}</td>
                <td data-rotulo="Situação"><Selo :tipo="m.vigente ? 'success' : 'neutro'" :texto="m.vigente ? 'Vigente' : 'Encerrado'" /> <Selo tipo="governance" texto="MFA" /></td>
                <td v-if="escrever" data-rotulo="Ações"><button v-if="m.vigente" type="button" class="btn btn-ghost" @click="encerrarMembro(m)">Encerrar período</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="aba === 'alteracoes'" class="card" role="tabpanel">
        <div class="card-head"><h2>Alterações contratuais</h2>
          <button v-if="escrever" type="button" class="btn btn-primary" @click="(alteracaoAberta = true), (arquivo = null), (errosAlt = {})"><Icone nome="mais" />Registrar alteração</button></div>
        <p class="small mid">Registros imutáveis (somente inclusão), com anexo em PDF, PNG ou JPEG de até 5 MB.</p>
        <div v-if="!alteracoes.length" class="vazio"><p>Nenhuma alteração registrada.</p></div>
        <ul v-else class="timeline">
          <li v-for="a in alteracoes" :key="a.id">
            <div class="row-between"><strong>{{ rotuloTipo(a.tipo) }} · {{ fmtDate(a.dataAlteracao) }}</strong><span class="small subtle">{{ a.createdByNome }} · {{ fmtDateTime(a.createdAt) }}</span></div>
            <div>{{ a.descricao }}</div>
            <button v-if="a.anexoNome" type="button" class="btn btn-ghost" @click="baixar(a)"><Icone nome="baixar" tamanho="sm" />{{ a.anexoNome }} ({{ fmtNumber(Math.ceil((a.anexoTamanho ?? 0) / 1024)) }} KB)</button>
          </li>
        </ul>
      </section>

      <section v-else class="card" role="tabpanel"><HistoricoLista :caminho="`${base}/historico`" :recarregar="versaoHist" /></section>

      <FormContratada v-if="editar" :contratada="c" :etag="etag" @fechar="editar = false" @salvo="(editar = false), carregar()" />
      <MudarSituacao v-if="situacao" :caminho="base" :ativo="c.situacao === 'ATIVO'" nome="contratada" @fechar="situacao = false" @concluido="(situacao = false), carregar()" />

      <Janela v-if="membroAberto" titulo="Incluir membro na equipe" @fechar="membroAberto = false">
        <p v-if="errosMembro._" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ errosMembro._ }}</p>
        <div class="form-grid">
          <Campo id="m-u" rotulo="Usuário (Core)" classe="col-12" :erro="errosMembro.usuarioId" obrigatorio v-slot="{ id, descricao, invalido }">
            <select :id="id" v-model="membro.usuarioId" class="input" :aria-describedby="descricao" :aria-invalid="invalido"><option value="" disabled>Selecione</option>
              <option v-for="u in usuarios" :key="u.id" :value="u.id">{{ u.nomeExibicao }}</option></select></Campo>
          <Campo id="m-p" rotulo="Papel" classe="col-12" obrigatorio v-slot="{ id }">
            <select :id="id" v-model="membro.papel" class="input"><option value="N2_GESTOR">N2 gestor (gestor da fábrica)</option><option value="N2_TECNICO">N2+ técnico</option></select></Campo>
          <Campo id="m-i" rotulo="Início" classe="col-6" :erro="errosMembro.inicio" obrigatorio v-slot="{ id }"><input :id="id" v-model="membro.inicio" type="date" class="input" /></Campo>
          <Campo id="m-f" rotulo="Fim (opcional)" classe="col-6" :erro="errosMembro.fim" v-slot="{ id }"><input :id="id" v-model="membro.fim" type="date" class="input" /></Campo>
        </div>
        <template #acoes><button type="button" class="btn btn-secondary" @click="membroAberto = false">Cancelar</button><button type="button" class="btn btn-primary" @click="salvarMembro">Incluir</button></template>
      </Janela>

      <Janela v-if="alteracaoAberta" titulo="Registrar alteração contratual" @fechar="alteracaoAberta = false">
        <p v-if="errosAlt._" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ errosAlt._ }}</p>
        <div class="form-grid">
          <Campo id="a-d" rotulo="Data da alteração" classe="col-6" :erro="errosAlt.dataAlteracao" obrigatorio v-slot="{ id }"><input :id="id" v-model="alt.dataAlteracao" type="date" class="input" /></Campo>
          <Campo id="a-t" rotulo="Tipo" classe="col-6" obrigatorio v-slot="{ id }">
            <select :id="id" v-model="alt.tipo" class="input"><option v-for="t in TIPOS_ALTERACAO" :key="t" :value="t">{{ rotuloTipo(t) }}</option></select></Campo>
          <Campo id="a-desc" rotulo="Descrição" classe="col-12" :erro="errosAlt.descricao" obrigatorio v-slot="{ id }"><textarea :id="id" v-model="alt.descricao" class="input" maxlength="2000" /></Campo>
          <Campo id="a-arq" rotulo="Anexo (opcional)" classe="col-12" :erro="errosAlt.anexo" ajuda="PDF, PNG ou JPEG, até 5 MB." v-slot="{ id, descricao }">
            <input :id="id" type="file" class="input" accept="application/pdf,image/png,image/jpeg" :aria-describedby="descricao"
                   @change="arquivo = ($event.target as HTMLInputElement).files?.[0] ?? null" /></Campo>
        </div>
        <template #acoes><button type="button" class="btn btn-secondary" @click="alteracaoAberta = false">Cancelar</button><button type="button" class="btn btn-primary" @click="salvarAlteracao">Registrar</button></template>
      </Janela>
    </template>
  </Estados>
</template>
