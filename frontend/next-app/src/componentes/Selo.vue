<script setup lang="ts">
/** Badge de status: a cor nunca é a única informação (texto + ícone, Arquitetura 12.9). */
import { computed } from 'vue'
import Icone from './Icone.vue'
import { rotulo } from '@/lib/format'

const props = defineProps<{ valor?: string | null; texto?: string; tipo?: string }>()

const MAPA: Record<string, [string, string]> = {
  ATIVO: ['success', 'ok'],
  VIGENTE: ['success', 'ok'],
  CONCLUIDA: ['success', 'ok'],
  EM_DIA: ['success', 'ok'],
  DESATIVADO: ['neutro', 'energia'],
  ENCERRADA: ['neutro', 'relogio'],
  RASCUNHO: ['accent', 'editar'],
  EM_AVALIACAO: ['governance', 'escudo'],
  PROCESSANDO: ['primary', 'relogio'],
  EM_RISCO: ['warning', 'alerta'],
  VIOLADO: ['danger', 'alerta'],
  FALHOU: ['danger', 'alerta'],
}

const tipo = computed(() => props.tipo ?? MAPA[props.valor ?? '']?.[0] ?? 'neutro')
const icone = computed(() => MAPA[props.valor ?? '']?.[1] ?? (tipo.value === 'warning' || tipo.value === 'danger' ? 'alerta' : 'info'))
</script>

<template>
  <span class="badge" :class="tipo !== 'neutro' ? `badge--${tipo}` : ''">
    <Icone :nome="icone" tamanho="sm" />
    {{ props.texto ?? rotulo(props.valor) }}
  </span>
</template>
