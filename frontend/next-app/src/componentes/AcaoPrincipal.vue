<script setup lang="ts">
/** Ação principal da tela: botão primário no cabeçalho (desktop) e FAB acima da bottom nav (mobile). */
import Icone from './Icone.vue'

const props = defineProps<{ rotulo: string; para?: string; icone?: string }>()
const emit = defineEmits<{ acionar: [] }>()
</script>

<template>
  <div class="page-actions-desktop">
    <RouterLink v-if="props.para" :to="props.para" class="btn btn-primary"><Icone :nome="props.icone ?? 'mais'" />{{ props.rotulo }}</RouterLink>
    <button v-else type="button" class="btn btn-primary" @click="emit('acionar')"><Icone :nome="props.icone ?? 'mais'" />{{ props.rotulo }}</button>
  </div>
  <Teleport to="body">
    <RouterLink v-if="props.para" :to="props.para" class="fab" :aria-label="props.rotulo"><Icone :nome="props.icone ?? 'mais'" tamanho="lg" /></RouterLink>
    <button v-else type="button" class="fab" :aria-label="props.rotulo" @click="emit('acionar')"><Icone :nome="props.icone ?? 'mais'" tamanho="lg" /></button>
  </Teleport>
</template>
