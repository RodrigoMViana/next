<script setup lang="ts">
/** RF10 / RN20: sistemas do Portfólio, vínculos com contratos e SLAs e sinalização de pendência. */
import { onMounted, reactive, ref, watch } from 'vue'
import { api, CFG, novaChave } from '@/api'
import type { SistemaPortfolio } from '@/api/tipos'
import Estados from '@/componentes/Estados.vue'
import Selo from '@/componentes/Selo.vue'
import Icone from '@/componentes/Icone.vue'
import Janela from '@/componentes/Janela.vue'
import Campo from '@/componentes/Campo.vue'
import { avisar, avisarErro } from '@/estado/avisos'
import { pode } from '@/estado/sessao'

const filtros = reactive({ termo: '', somentePendentes: false })
const sistemas = ref<SistemaPortfolio[]>([])
const disponivel = ref(true)
const carregando = ref(true)
const erro = ref<string | null>(null)

async function carregar() {
  carregando.value = true
  erro.value = null
  try {
    const r = await api.get<{ portfolioDisponivel: boolean; sistemas: SistemaPortfolio[] }>(`${CFG}/portfolio/sistemas`, filtros)
    sistemas.value = r.dados.sistemas
    disponivel.value = r.dados.portfolioDisponivel
  } catch (e) {
    erro.value = (e as Error).message
  } finally {
    carregando.value = false
  }
}
onMounted(carregar)
watch(filtros, carregar)

// Ferramenta de homologação: simula o evento portfolio.sistema.ativado (consumo idempotente)
const evento = reactive({ aberto: false, sistemaId: '', nome: '', id: '' })
function abrirEvento() {
  Object.assign(evento, { aberto: true, sistemaId: sistemas.value[0]?.id ?? '', nome: '', id: novaChave() })
}
async function enviarEvento() {
  try {
    const r = await api.post<{ processado: boolean; mensagem: string }>(`${CFG}/eventos`, { id: evento.id, type: 'portfolio.sistema.ativado', version: 1,
      occurredAt: new Date().toISOString(), source: 'next-portfolio', payload: { sistemaId: evento.sistemaId, nome: evento.nome || undefined } })
    avisar(r.dados.processado ? 'Evento processado.' : 'Evento repetido ignorado (idempotência).', r.dados.processado ? 'sucesso' : 'aviso', r.dados.mensagem)
    carregar()
  } catch (e) {
    avisarErro(e)
  }
}
</script>

<template>
  <div class="page-head">
    <div>
      <nav class="breadcrumb" aria-label="Trilha"><RouterLink to="/">Início</RouterLink><span aria-hidden="true">›</span><span class="atual">Portfólio</span></nav>
      <h1>Sistemas do Portfólio</h1>
      <p class="small mid">Consulta à API do Portfólio (SUBTI). O vínculo usa o identificador do sistema, sem copiar o cadastro (RN19).</p>
    </div>
    <button v-if="pode('simularEventos')" type="button" class="btn btn-secondary" @click="abrirEvento"><Icone nome="atualizar" />Simular evento do Portfólio</button>
  </div>
  <p v-if="!disponivel" class="alerta alerta--aviso"><Icone nome="alerta" />O Portfólio não respondeu. Exibindo apenas o que foi possível carregar.</p>
  <section class="card">
    <div class="filtros" role="search">
      <div class="campo"><label for="p-t">Buscar</label><input id="p-t" v-model="filtros.termo" class="input" placeholder="Sigla ou nome" /></div>
      <label class="check"><input v-model="filtros.somentePendentes" type="checkbox" />Somente sistemas sem SLA vigente</label>
    </div>
    <Estados :carregando="carregando" :erro="erro" :vazio="!sistemas.length" @tentar-novamente="carregar">
      <div class="tabela-wrap">
        <table class="tabela responsiva">
          <thead><tr><th>Sistema</th><th>Área</th><th>Contratos</th><th>SLAs</th><th>Configuração</th></tr></thead>
          <tbody>
            <tr v-for="s in sistemas" :key="s.id">
              <td data-rotulo="Sistema"><span class="mono">{{ s.sigla }}</span> {{ s.nome }}<div><Selo :valor="s.situacao === 'ATIVO' ? 'ATIVO' : null" :texto="s.situacao === 'ATIVO' ? 'Ativo no Portfólio' : 'Descontinuado'" :tipo="s.situacao === 'ATIVO' ? undefined : 'neutro'" /></div></td>
              <td data-rotulo="Área">{{ s.areaResponsavel }}</td>
              <td data-rotulo="Contratos"><RouterLink v-for="c in s.contratos" :key="c.id" :to="`/contratos/${c.id}`" class="mono">{{ c.codigo }} </RouterLink><span v-if="!s.contratos.length" class="subtle">—</span></td>
              <td data-rotulo="SLAs"><RouterLink v-for="c in s.slas" :key="c.id" :to="`/slas/${c.id}`" class="mono">{{ c.codigo }} </RouterLink><span v-if="!s.slas.length" class="subtle">—</span></td>
              <td data-rotulo="Configuração">
                <Selo v-if="s.pendenciaConfiguracao" tipo="warning" texto="Pendente: sem SLA vigente" />
                <Selo v-else-if="s.temSlaVigente" tipo="success" texto="SLA vigente" />
                <span v-else class="subtle">Não se aplica</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </Estados>
  </section>
  <Janela v-if="evento.aberto" titulo="Simular evento portfolio.sistema.ativado" @fechar="evento.aberto = false">
    <p class="small mid">Ferramenta de homologação: envia ao módulo o evento que o Portfólio publicará. Reenviar o mesmo identificador demonstra o consumo idempotente.</p>
    <div class="form-grid">
      <Campo id="e-s" rotulo="Sistema" classe="col-12" v-slot="{ id }"><select :id="id" v-model="evento.sistemaId" class="input"><option v-for="s in sistemas" :key="s.id" :value="s.id">{{ s.sigla }} – {{ s.nome }}</option></select></Campo>
      <Campo id="e-n" rotulo="Novo nome de exibição (opcional)" classe="col-12" ajuda="Atualiza a cópia do nome nos vínculos (RN19)." v-slot="{ id, descricao }"><input :id="id" v-model="evento.nome" class="input" :aria-describedby="descricao" /></Campo>
      <Campo id="e-id" rotulo="Identificador do evento" classe="col-12" v-slot="{ id }"><input :id="id" v-model="evento.id" class="input mono" /></Campo>
    </div>
    <template #acoes><button type="button" class="btn btn-secondary" @click="evento.aberto = false">Fechar</button><button type="button" class="btn btn-primary" @click="enviarEvento">Enviar evento</button></template>
  </Janela>
</template>
