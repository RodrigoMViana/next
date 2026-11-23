<script setup lang="ts">
/** Histórico de alterações do cadastro: o que mudou, quem alterou, quando e o valor anterior (RF16). */
import { onMounted, ref, watch } from 'vue'
import { api } from '@/api'
import type { Historico } from '@/api/tipos'
import { fmtDateTime } from '@/lib/format'
import Estados from './Estados.vue'

const props = defineProps<{ caminho: string; recarregar?: number }>()
const itens = ref<Historico[]>([])
const carregando = ref(true)
const erro = ref<string | null>(null)

const ACOES: Record<string, string> = {
  CRIACAO: 'Criação', ALTERACAO: 'Alteração', DESATIVACAO: 'Desativação', REATIVACAO: 'Reativação', ADITIVO: 'Aditivo',
  FISCAL_DESIGNACAO: 'Designação de fiscal', FISCAL_SUBSTITUICAO: 'Substituição de fiscal', FISCAL_ENCERRAMENTO: 'Encerramento de fiscal',
  SLA_NOVA_VERSAO: 'Nova versão', SLA_EM_AVALIACAO: 'Enviado para avaliação', SLA_PARECER: 'Parecer da contratada', SLA_VIGENCIA: 'Decisão de vigência',
  SLA_DEVOLVIDO: 'Devolvido para rascunho', SLA_ENCERRADA: 'Versão encerrada', EQUIPE_INCLUSAO: 'Inclusão na equipe', EQUIPE_ENCERRAMENTO: 'Saída da equipe',
  ALTERACAO_CONTRATUAL: 'Alteração contratual', SISTEMA_VINCULADO: 'Sistema vinculado', SISTEMA_DESVINCULADO: 'Sistema desvinculado',
}

async function carregar() {
  carregando.value = true
  erro.value = null
  try {
    itens.value = (await api.get<Historico[]>(props.caminho)).dados
  } catch (e) {
    erro.value = (e as Error).message
  } finally {
    carregando.value = false
  }
}
onMounted(carregar)
watch(() => props.recarregar, carregar)
</script>

<template>
  <Estados :carregando="carregando" :erro="erro" :vazio="!itens.length" texto-vazio="Sem alterações registradas." @tentar-novamente="carregar">
    <ul class="timeline">
      <li v-for="h in itens" :key="h.id">
        <div class="row-between">
          <strong>{{ ACOES[h.acao] ?? h.acao }}<span v-if="h.campo"> · {{ h.campo }}</span></strong>
          <span class="small subtle">{{ fmtDateTime(h.ocorridoEm) }} · {{ h.usuarioNome }}</span>
        </div>
        <div v-if="h.acao === 'ALTERACAO'" class="small">
          <span class="diff-antes">{{ h.valorAnterior ?? '(vazio)' }}</span> → <span class="diff-depois">{{ h.valorNovo ?? '(vazio)' }}</span>
        </div>
        <div v-else-if="h.valorNovo" class="small mid">{{ h.valorNovo }}</div>
        <div v-if="h.justificativa" class="small subtle">Justificativa: {{ h.justificativa }}</div>
      </li>
    </ul>
  </Estados>
</template>
