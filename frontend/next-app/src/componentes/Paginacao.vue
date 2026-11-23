<script setup lang="ts">
import { fmtNumber } from '@/lib/format'

const props = defineProps<{ page: number; totalPages: number; totalElements: number }>()
const emit = defineEmits<{ ir: [pagina: number] }>()
</script>

<template>
  <nav class="paginacao" aria-label="Paginação">
    <span class="small mid">{{ fmtNumber(props.totalElements) }} registro(s) · página {{ props.totalPages ? props.page + 1 : 0 }} de {{ props.totalPages }}</span>
    <div class="row">
      <button type="button" class="btn btn-secondary" :disabled="props.page <= 0" @click="emit('ir', props.page - 1)">Anterior</button>
      <button type="button" class="btn btn-secondary" :disabled="props.page + 1 >= props.totalPages" @click="emit('ir', props.page + 1)">Próxima</button>
    </div>
  </nav>
</template>
