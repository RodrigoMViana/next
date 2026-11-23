<script setup lang="ts">
/** Estados de carregamento (esqueleto), erro e vazio, tratados em toda tela que busca dados (12.11). */
import Icone from './Icone.vue'

const props = defineProps<{ carregando: boolean; erro?: string | null; vazio?: boolean; textoVazio?: string; linhas?: number }>()
const emit = defineEmits<{ tentarNovamente: [] }>()
</script>

<template>
  <div v-if="props.carregando" aria-busy="true" aria-live="polite">
    <span class="sr-only">Carregando…</span>
    <div v-for="i in props.linhas ?? 4" :key="i" class="skeleton" :class="i % 3 === 0 ? 'w60' : i % 2 === 0 ? 'w80' : ''" />
  </div>
  <div v-else-if="props.erro" class="alerta alerta--erro" role="alert">
    <Icone nome="alerta" />
    <div class="grow">
      <strong>Não foi possível carregar.</strong>
      <div>{{ props.erro }}</div>
    </div>
    <button type="button" class="btn btn-secondary" @click="emit('tentarNovamente')">Tentar novamente</button>
  </div>
  <div v-else-if="props.vazio" class="vazio">
    <div class="icone"><Icone nome="busca" tamanho="lg" /></div>
    <p>{{ props.textoVazio ?? 'Nenhum registro encontrado.' }}</p>
    <slot name="acao" />
  </div>
  <slot v-else />
</template>
