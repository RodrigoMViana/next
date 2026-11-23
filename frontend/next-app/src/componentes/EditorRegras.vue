<script setup lang="ts">
/** Regras parametrizáveis do SLA: classe, indicador, meta, forma de contagem e calendário (RN11 a RN13). */
import type { Calendario, RegraSla } from '@/api/tipos'
import Icone from './Icone.vue'

const regras = defineModel<RegraSla[]>({ required: true })
const props = defineProps<{ calendarios: Calendario[]; erros?: Record<string, string> }>()

function nova(): RegraSla {
  return { classe: '', indicador: '', metaValor: 1, metaUnidade: 'HORAS', formaContagem: 'HORARIO_COMERCIAL', calendarioId: props.calendarios[0]?.id ?? null, permitePausa: true, observacao: null }
}
const erro = (i: number, campo?: string) => props.erros?.[`regras[${i}]${campo ? '.' + campo : ''}`] ?? (campo ? undefined : props.erros?.[`regras[${i}]`])
</script>

<template>
  <div>
    <p class="small mid">Sem número fixo de níveis, medidas ou prazos: inclua quantas regras o acordo exigir. Contagem em horário comercial exige calendário.</p>
    <div v-for="(r, i) in regras" :key="i" class="editor-linha">
      <div class="campo"><label :for="`r-c-${i}`">Classe</label><input :id="`r-c-${i}`" v-model="r.classe" class="input" placeholder="Ex.: Prioridade crítica" :aria-invalid="!!erro(i, 'classe')" /></div>
      <div class="campo"><label :for="`r-i-${i}`">Indicador</label><input :id="`r-i-${i}`" v-model="r.indicador" class="input" placeholder="Ex.: Tempo de solução" :aria-invalid="!!erro(i, 'indicador')" /></div>
      <div class="campo"><label :for="`r-m-${i}`">Meta</label><input :id="`r-m-${i}`" v-model.number="r.metaValor" type="number" min="0" step="0.01" class="input" /></div>
      <div class="campo"><label :for="`r-u-${i}`">Unidade</label>
        <select :id="`r-u-${i}`" v-model="r.metaUnidade" class="input">
          <option value="MINUTOS">Minutos</option><option value="HORAS">Horas</option><option value="DIAS_UTEIS">Dias úteis</option>
          <option value="DIAS_CORRIDOS">Dias corridos</option><option value="PERCENTUAL">Percentual (%)</option>
        </select></div>
      <div class="campo"><label :for="`r-f-${i}`">Contagem</label>
        <select :id="`r-f-${i}`" v-model="r.formaContagem" class="input"><option value="CORRIDO_24X7">Corrido (24x7)</option><option value="HORARIO_COMERCIAL">Horário comercial</option></select></div>
      <div class="campo"><label :for="`r-cal-${i}`">Calendário</label>
        <select :id="`r-cal-${i}`" v-model="r.calendarioId" class="input" :disabled="r.formaContagem === 'CORRIDO_24X7'">
          <option :value="null">—</option><option v-for="c in props.calendarios" :key="c.id" :value="c.id">{{ c.nome }}</option>
        </select></div>
      <div class="row">
        <label class="check" :title="'Permite pausa justificada (RN13)'"><input v-model="r.permitePausa" type="checkbox" />Pausa</label>
        <button type="button" class="btn-icon" :aria-label="`Remover regra ${i + 1}`" @click="regras.splice(i, 1)"><Icone nome="lixeira" /></button>
      </div>
      <p v-if="erro(i) || erro(i, 'classe') || erro(i, 'indicador') || erro(i, 'calendarioId')" class="campo"><span class="erro" role="alert">
        <Icone nome="alerta" tamanho="sm" />{{ erro(i) || erro(i, 'classe') || erro(i, 'indicador') || erro(i, 'calendarioId') }}</span></p>
    </div>
    <button type="button" class="btn btn-secondary" @click="regras.push(nova())"><Icone nome="mais" />Adicionar regra</button>
  </div>
</template>
