<script setup lang="ts">
/** Detalhe do contrato: valores originais e efetivos, aditivos (RF03), fiscais (RF04), sistemas (RF10) e histórico (RF16). */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, CFG, novaChave } from '@/api'
import { ProblemaError } from '@/api/cliente'
import type { ContratoDetalhe, Fiscal, SistemaPortfolio, TipoFiscal, UsuarioCore } from '@/api/tipos'
import Estados from '@/componentes/Estados.vue'
import Selo from '@/componentes/Selo.vue'
import Icone from '@/componentes/Icone.vue'
import Janela from '@/componentes/Janela.vue'
import Campo from '@/componentes/Campo.vue'
import HistoricoLista from '@/componentes/HistoricoLista.vue'
import MudarSituacao from '@/componentes/MudarSituacao.vue'
import { avisar, avisarErro } from '@/estado/avisos'
import { pode } from '@/estado/sessao'
import { fmtDate, fmtDateTime, fmtMoney, fmtNumber, hojeIso, rotulo } from '@/lib/format'

const route = useRoute()
const id = route.params.id as string
const base = `${CFG}/contratos/${id}`
const c = ref<ContratoDetalhe | null>(null)
const carregando = ref(true)
const erro = ref<string | null>(null)
const aba = ref('dados')
const versaoHistorico = ref(0)
const escrever = pode('escreverCadastros')

async function carregar() {
  carregando.value = true
  erro.value = null
  try {
    c.value = (await api.get<ContratoDetalhe>(base)).dados
  } catch (e) {
    erro.value = (e as Error).message
  } finally {
    carregando.value = false
  }
}
onMounted(carregar)

function atualizar(d: ContratoDetalhe) {
  c.value = d
  versaoHistorico.value++
}

// ---- situação
const situacaoAberta = ref(false)

// ---- aditivo
const aditivoAberto = ref(false)
const aditivo = reactive({ numero: '', aditivoPrazo: false, aditivoValor: false, novaVigenciaFim: '', valorAcrescimo: '', dataAssinatura: hojeIso(), justificativa: '' })
const errosAditivo = ref<Record<string, string>>({})
let chaveAditivo = novaChave()
function abrirAditivo() {
  Object.assign(aditivo, { numero: `${(c.value?.aditivos.length ?? 0) + 1}º TA`, aditivoPrazo: false, aditivoValor: false, novaVigenciaFim: '', valorAcrescimo: '', dataAssinatura: hojeIso(), justificativa: '' })
  errosAditivo.value = {}
  chaveAditivo = novaChave()
  aditivoAberto.value = true
}
async function salvarAditivo() {
  errosAditivo.value = {}
  try {
    const r = await api.post<ContratoDetalhe>(`${base}/aditivos`, {
      ...aditivo, novaVigenciaFim: aditivo.aditivoPrazo ? aditivo.novaVigenciaFim || null : null,
      valorAcrescimo: aditivo.aditivoValor && aditivo.valorAcrescimo !== '' ? Number(aditivo.valorAcrescimo) : null,
    }, { chaveIdempotencia: chaveAditivo })
    atualizar(r.dados)
    aditivoAberto.value = false
    avisar('Aditivo registrado. Vigência e valor efetivos recalculados.')
  } catch (e) {
    if (e instanceof ProblemaError) errosAditivo.value = { _: e.message, ...e.campos }
    else avisarErro(e)
  }
}

// ---- fiscais
const usuarios = ref<UsuarioCore[]>([])
async function carregarUsuarios() {
  if (!usuarios.value.length) usuarios.value = (await api.get<UsuarioCore[]>(`${CFG}/usuarios`)).dados.filter((u) => !u.contratadaId)
}
const fiscalModo = ref<'' | 'designar' | 'substituir' | 'encerrar'>('')
const fiscalAlvo = ref<Fiscal | null>(null)
const fiscal = reactive({ usuarioId: '', tipoFiscal: 'FISCAL_TECNICO' as TipoFiscal, inicio: hojeIso(), fim: '', motivo: '' })
const errosFiscal = ref<Record<string, string>>({})
async function abrirFiscal(modo: 'designar' | 'substituir' | 'encerrar', alvo?: Fiscal) {
  await carregarUsuarios()
  fiscalAlvo.value = alvo ?? null
  Object.assign(fiscal, { usuarioId: '', tipoFiscal: alvo?.tipoFiscal ?? 'FISCAL_TECNICO', inicio: hojeIso(), fim: modo === 'encerrar' ? hojeIso() : '', motivo: '' })
  errosFiscal.value = {}
  fiscalModo.value = modo
}
async function salvarFiscal() {
  errosFiscal.value = {}
  try {
    let r
    if (fiscalModo.value === 'designar') r = await api.post<ContratoDetalhe>(`${base}/fiscais`, { usuarioId: fiscal.usuarioId, tipoFiscal: fiscal.tipoFiscal, inicio: fiscal.inicio, fim: fiscal.fim || null })
    else if (fiscalModo.value === 'substituir') r = await api.post<ContratoDetalhe>(`${base}/fiscais/${fiscalAlvo.value!.id}/substituicao`, { usuarioId: fiscal.usuarioId, inicio: fiscal.inicio, motivo: fiscal.motivo })
    else r = await api.post<ContratoDetalhe>(`${base}/fiscais/${fiscalAlvo.value!.id}/encerramento`, { fim: fiscal.fim, motivo: fiscal.motivo })
    atualizar(r.dados)
    avisar(fiscalModo.value === 'substituir' ? 'Fiscal substituído. A designação anterior foi encerrada.' : 'Comissão de fiscalização atualizada.')
    fiscalModo.value = ''
  } catch (e) {
    if (e instanceof ProblemaError) errosFiscal.value = { _: e.message, ...e.campos }
    else avisarErro(e)
  }
}

// ---- sistemas
const sistemasPortfolio = ref<SistemaPortfolio[]>([])
const sistemaEscolhido = ref('')
const sistemaAberto = ref(false)
async function abrirSistema() {
  sistemasPortfolio.value = (await api.get<{ sistemas: SistemaPortfolio[] }>(`${CFG}/portfolio/sistemas`)).dados.sistemas
  sistemaEscolhido.value = ''
  sistemaAberto.value = true
}
async function vincular() {
  try {
    atualizar((await api.post<ContratoDetalhe>(`${base}/sistemas`, { sistemaId: sistemaEscolhido.value })).dados)
    sistemaAberto.value = false
    avisar('Sistema vinculado ao contrato.')
  } catch (e) {
    avisarErro(e)
  }
}
async function desvincular(sid: string) {
  if (!confirm('Desvincular o sistema deste contrato?')) return
  try {
    atualizar((await api.del<ContratoDetalhe>(`${base}/sistemas/${sid}`)).dados)
    avisar('Sistema desvinculado.')
  } catch (e) {
    avisarErro(e)
  }
}

const abas = computed<Array<[string, string]>>(() => [
  ['dados', 'Dados e itens'], ['aditivos', `Aditivos (${c.value?.aditivos.length ?? 0})`], ['fiscais', 'Fiscalização'],
  ['sistemas', `Sistemas (${c.value?.sistemas.length ?? 0})`], ['historico', 'Histórico'],
])
</script>

<template>
  <Estados :carregando="carregando" :erro="erro" :linhas="8" @tentar-novamente="carregar">
    <template v-if="c">
      <div class="page-head">
        <div>
          <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><RouterLink to="/contratos">Contratos</RouterLink><span aria-hidden="true">›</span><span class="atual mono">{{ c.numero }}</span></nav>
          <h1 class="mono">{{ c.numero }}</h1>
          <p class="mid">{{ c.objeto }}</p>
          <div class="row">
            <Selo :valor="c.situacao" />
            <Selo v-if="c.situacao === 'ATIVO'" :tipo="c.vigente ? 'primary' : 'warning'" :texto="c.vigente ? 'Vigente' : 'Fora da vigência'" />
            <Selo v-if="!c.temFiscalVigente" tipo="danger" texto="Sem fiscal vigente" />
          </div>
        </div>
        <div v-if="escrever" class="row">
          <RouterLink v-if="c.situacao === 'ATIVO'" :to="`/contratos/${c.id}/editar`" class="btn btn-secondary"><Icone nome="editar" />Editar</RouterLink>
          <button type="button" class="btn btn-secondary" @click="situacaoAberta = true"><Icone nome="energia" />{{ c.situacao === 'ATIVO' ? 'Desativar' : 'Reativar' }}</button>
        </div>
      </div>
      <p v-if="c.justificativaSituacao" class="alerta alerta--info"><Icone nome="info" />Justificativa da situação: {{ c.justificativaSituacao }}</p>

      <div class="kpis mb-4">
        <div class="kpi"><div class="valor num">{{ fmtMoney(c.valorOriginal) }}</div><div class="rotulo">Valor original (itens)</div></div>
        <div class="kpi success"><div class="valor num">{{ fmtMoney(c.valorEfetivo) }}</div><div class="rotulo">Valor efetivo (com aditivos)</div></div>
        <div class="kpi"><div class="valor">{{ fmtDate(c.vigenciaFim) }}</div><div class="rotulo">Fim da vigência original</div></div>
        <div class="kpi" :class="c.vigente ? 'success' : 'warning'"><div class="valor">{{ fmtDate(c.vigenciaFimEfetiva) }}</div><div class="rotulo">Fim da vigência efetiva</div></div>
      </div>

      <div class="abas" role="tablist">
        <button v-for="[k, t] in abas" :key="k" type="button" role="tab" :aria-selected="aba === k" @click="aba = k">{{ t }}</button>
      </div>

      <section v-if="aba === 'dados'" class="card" role="tabpanel">
        <dl class="dl">
          <div><dt>Contratada</dt><dd><RouterLink :to="`/contratadas/${c.contratada.id}`">{{ c.contratada.nome }}</RouterLink><div class="small mono subtle">{{ c.contratada.codigo }}</div></dd></div>
          <div><dt>Vigência</dt><dd>{{ fmtDate(c.vigenciaInicio) }} a {{ fmtDate(c.vigenciaFimEfetiva) }}</dd></div>
          <div><dt>Calendário de SLA</dt><dd>{{ c.calendario.nome }}<div class="small subtle">{{ c.calendario.codigo }}</div></dd></div>
          <div><dt>Fontes de recursos</dt><dd>{{ c.fontes.map((f) => `${f.codigo} – ${f.nome}`).join('; ') || '—' }}</dd></div>
          <div><dt>Cadastrado em</dt><dd>{{ fmtDateTime(c.createdAt) }}</dd></div>
          <div><dt>Última alteração</dt><dd>{{ fmtDateTime(c.updatedAt) }}</dd></div>
        </dl>
        <h3 class="mt-5 mb-2">Itens contratados</h3>
        <div class="tabela-wrap">
          <table class="tabela responsiva">
            <thead><tr><th>Serviço</th><th>Unidade</th><th class="right">Volume</th><th class="right">Valor unitário</th><th class="right">Valor total</th></tr></thead>
            <tbody>
              <tr v-for="i in c.itens" :key="i.id">
                <td data-rotulo="Serviço"><span class="mono">{{ i.servico.codigo }}</span> {{ i.servico.nome }}</td>
                <td data-rotulo="Unidade">{{ i.unidadeMedida.codigo }}</td>
                <td data-rotulo="Volume" class="right num">{{ fmtNumber(i.volume) }}</td>
                <td data-rotulo="Valor unitário" class="right num">{{ fmtMoney(i.valorUnitario) }}</td>
                <td data-rotulo="Valor total" class="right num">{{ fmtMoney(i.valorTotal) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="small subtle mt-2">Valor total = volume × valor unitário (calculado, não editável — RN02).</p>
      </section>

      <section v-else-if="aba === 'aditivos'" class="card" role="tabpanel">
        <div class="card-head">
          <h2>Aditivos</h2>
          <button v-if="escrever && c.situacao === 'ATIVO'" type="button" class="btn btn-primary" @click="abrirAditivo"><Icone nome="mais" />Registrar aditivo</button>
        </div>
        <p class="small mid">Aditivos só admitem inclusão: os valores originais do contrato são preservados (RN05).</p>
        <div v-if="!c.aditivos.length" class="vazio"><p>Nenhum aditivo registrado.</p></div>
        <div v-else class="tabela-wrap">
          <table class="tabela responsiva">
            <thead><tr><th>Número</th><th>Assinatura</th><th>Tipo</th><th>Nova vigência</th><th class="right">Valor</th><th>Justificativa</th></tr></thead>
            <tbody>
              <tr v-for="a in c.aditivos" :key="a.id">
                <td data-rotulo="Número">{{ a.numero }}</td>
                <td data-rotulo="Assinatura">{{ fmtDate(a.dataAssinatura) }}</td>
                <td data-rotulo="Tipo"><Selo v-if="a.aditivoPrazo" tipo="primary" texto="Prazo" /> <Selo v-if="a.aditivoValor" tipo="accent" texto="Valor" /></td>
                <td data-rotulo="Nova vigência">{{ fmtDate(a.novaVigenciaFim) }}</td>
                <td data-rotulo="Valor" class="right num">{{ fmtMoney(a.valorAcrescimo) }}</td>
                <td data-rotulo="Justificativa">{{ a.justificativa }}<div class="small subtle">{{ a.createdByNome }} · {{ fmtDateTime(a.createdAt) }}</div></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="aba === 'fiscais'" class="card" role="tabpanel">
        <div class="card-head">
          <h2>Comissão de fiscalização</h2>
          <button v-if="escrever" type="button" class="btn btn-primary" @click="abrirFiscal('designar')"><Icone nome="mais" />Designar fiscal</button>
        </div>
        <p class="small mid">Cada contrato deve ter ao menos um fiscal com designação vigente. A substituição encerra a designação anterior (RN06).</p>
        <div class="tabela-wrap">
          <table class="tabela responsiva">
            <thead><tr><th>Fiscal</th><th>Tipo</th><th>Período</th><th>Situação</th><th v-if="escrever"><span class="sr-only">Ações</span></th></tr></thead>
            <tbody>
              <tr v-for="f in c.fiscais" :key="f.id">
                <td data-rotulo="Fiscal">{{ f.nomeExibicao }}</td>
                <td data-rotulo="Tipo">{{ rotulo(f.tipoFiscal) }}</td>
                <td data-rotulo="Período">{{ fmtDate(f.inicio) }} a {{ f.fim ? fmtDate(f.fim) : 'sem término' }}<div v-if="f.motivoEncerramento" class="small subtle">{{ f.motivoEncerramento }}</div></td>
                <td data-rotulo="Situação"><Selo :tipo="f.vigente ? 'success' : 'neutro'" :texto="f.vigente ? 'Vigente' : 'Encerrada'" /></td>
                <td v-if="escrever" data-rotulo="Ações" class="nowrap">
                  <template v-if="f.vigente">
                    <button type="button" class="btn btn-ghost" @click="abrirFiscal('substituir', f)">Substituir</button>
                    <button type="button" class="btn btn-ghost" @click="abrirFiscal('encerrar', f)">Encerrar</button>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="aba === 'sistemas'" class="card" role="tabpanel">
        <div class="card-head">
          <h2>Sistemas do Portfólio</h2>
          <button v-if="escrever" type="button" class="btn btn-primary" @click="abrirSistema"><Icone nome="link" />Vincular sistema</button>
        </div>
        <p class="small mid">O vínculo guarda o identificador do sistema no Portfólio e só o nome de exibição (RN19).</p>
        <div v-if="!c.sistemas.length" class="vazio"><p>Nenhum sistema vinculado.</p></div>
        <ul v-else class="stack lista-limpa">
          <li v-for="s in c.sistemas" :key="s.sistemaId" class="row-between">
            <span>{{ s.sistemaNome }} <span class="small subtle mono">{{ s.sistemaId }}</span></span>
            <button v-if="escrever" type="button" class="btn btn-ghost" @click="desvincular(s.sistemaId)">Desvincular</button>
          </li>
        </ul>
      </section>

      <section v-else class="card" role="tabpanel">
        <HistoricoLista :caminho="`${base}/historico`" :recarregar="versaoHistorico" />
      </section>

      <MudarSituacao v-if="situacaoAberta" :caminho="base" :ativo="c.situacao === 'ATIVO'" nome="contrato" justificativa-obrigatoria
                     @fechar="situacaoAberta = false" @concluido="(situacaoAberta = false), carregar(), versaoHistorico++" />

      <Janela v-if="aditivoAberto" titulo="Registrar aditivo" @fechar="aditivoAberto = false">
        <p v-if="errosAditivo._" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ errosAditivo._ }}</p>
        <div class="form-grid">
          <Campo id="ad-num" rotulo="Número" classe="col-6" :erro="errosAditivo.numero" obrigatorio v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="aditivo.numero" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo id="ad-data" rotulo="Data de assinatura" classe="col-6" :erro="errosAditivo.dataAssinatura" obrigatorio v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="aditivo.dataAssinatura" type="date" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <div class="col-12 row">
            <label class="check"><input v-model="aditivo.aditivoPrazo" type="checkbox" />Aditivo de prazo</label>
            <label class="check"><input v-model="aditivo.aditivoValor" type="checkbox" />Aditivo de valor</label>
          </div>
          <Campo v-if="aditivo.aditivoPrazo" id="ad-fim" rotulo="Nova data de fim" classe="col-6" :erro="errosAditivo.novaVigenciaFim" obrigatorio
                 :ajuda="`Vigência efetiva atual: ${fmtDate(c.vigenciaFimEfetiva)}`" v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="aditivo.novaVigenciaFim" type="date" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo v-if="aditivo.aditivoValor" id="ad-valor" rotulo="Valor (R$)" classe="col-6" :erro="errosAditivo.valorAcrescimo" obrigatorio
                 ajuda="Use valor negativo para supressão." v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="aditivo.valorAcrescimo" type="number" step="0.01" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo id="ad-just" rotulo="Justificativa" classe="col-12" :erro="errosAditivo.justificativa || errosAditivo.aditivoPrazo" obrigatorio v-slot="{ id, descricao, invalido }">
            <textarea :id="id" v-model="aditivo.justificativa" class="input" maxlength="2000" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
        </div>
        <template #acoes>
          <button type="button" class="btn btn-secondary" @click="aditivoAberto = false">Cancelar</button>
          <button type="button" class="btn btn-primary" @click="salvarAditivo">Registrar aditivo</button>
        </template>
      </Janela>

      <Janela v-if="fiscalModo" :titulo="fiscalModo === 'designar' ? 'Designar fiscal' : fiscalModo === 'substituir' ? `Substituir ${fiscalAlvo?.nomeExibicao}` : `Encerrar designação de ${fiscalAlvo?.nomeExibicao}`" @fechar="fiscalModo = ''">
        <p v-if="errosFiscal._" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ errosFiscal._ }}</p>
        <div class="form-grid">
          <Campo v-if="fiscalModo !== 'encerrar'" id="fi-usu" :rotulo="fiscalModo === 'substituir' ? 'Novo fiscal' : 'Fiscal'" classe="col-12" :erro="errosFiscal.usuarioId" obrigatorio v-slot="{ id, descricao, invalido }">
            <select :id="id" v-model="fiscal.usuarioId" class="input" :aria-describedby="descricao" :aria-invalid="invalido">
              <option value="" disabled>Selecione</option>
              <option v-for="u in usuarios" :key="u.id" :value="u.id">{{ u.nomeExibicao }}</option>
            </select></Campo>
          <Campo v-if="fiscalModo === 'designar'" id="fi-tipo" rotulo="Tipo de fiscal" classe="col-6" obrigatorio v-slot="{ id }">
            <select :id="id" v-model="fiscal.tipoFiscal" class="input">
              <option v-for="t in ['GESTOR_CONTRATO', 'FISCAL_TECNICO', 'FISCAL_ADMINISTRATIVO', 'FISCAL_REQUISITANTE']" :key="t" :value="t">{{ rotulo(t) }}</option>
            </select></Campo>
          <Campo v-if="fiscalModo !== 'encerrar'" id="fi-ini" rotulo="Início da designação" classe="col-6" :erro="errosFiscal.inicio" obrigatorio v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="fiscal.inicio" type="date" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo v-if="fiscalModo !== 'substituir'" id="fi-fim" :rotulo="fiscalModo === 'encerrar' ? 'Fim da designação' : 'Fim (opcional)'" classe="col-6" :erro="errosFiscal.fim" :obrigatorio="fiscalModo === 'encerrar'" v-slot="{ id, descricao, invalido }">
            <input :id="id" v-model="fiscal.fim" type="date" class="input" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
          <Campo v-if="fiscalModo !== 'designar'" id="fi-mot" rotulo="Motivo" classe="col-12" :erro="errosFiscal.motivo" obrigatorio v-slot="{ id, descricao, invalido }">
            <textarea :id="id" v-model="fiscal.motivo" class="input" maxlength="500" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
        </div>
        <template #acoes>
          <button type="button" class="btn btn-secondary" @click="fiscalModo = ''">Cancelar</button>
          <button type="button" class="btn btn-primary" @click="salvarFiscal">Confirmar</button>
        </template>
      </Janela>

      <Janela v-if="sistemaAberto" titulo="Vincular sistema do Portfólio" @fechar="sistemaAberto = false">
        <Campo id="sis" rotulo="Sistema" obrigatorio v-slot="{ id }">
          <select :id="id" v-model="sistemaEscolhido" class="input">
            <option value="" disabled>Selecione</option>
            <option v-for="s in sistemasPortfolio.filter((x) => !c!.sistemas.some((y) => y.sistemaId === x.id))" :key="s.id" :value="s.id">{{ s.sigla }} – {{ s.nome }} ({{ s.situacao }})</option>
          </select>
        </Campo>
        <template #acoes>
          <button type="button" class="btn btn-secondary" @click="sistemaAberto = false">Cancelar</button>
          <button type="button" class="btn btn-primary" :disabled="!sistemaEscolhido" @click="vincular">Vincular</button>
        </template>
      </Janela>
    </template>
  </Estados>
</template>

<style scoped>
.lista-limpa { list-style: none; margin: 0; padding: 0; }
.lista-limpa li { border-bottom: 1px solid var(--nx-color-border); padding: var(--nx-space-2) 0; }
</style>
