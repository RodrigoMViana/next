<script setup lang="ts">
/** Rótulo + controle + mensagem de erro ligada por aria-describedby (Arquitetura 12.10). */
import Icone from './Icone.vue'

const props = defineProps<{ id: string; rotulo: string; erro?: string | null; ajuda?: string; obrigatorio?: boolean; classe?: string }>()
</script>

<template>
  <div class="campo" :class="props.classe">
    <label :for="props.id">
      {{ props.rotulo }}<span v-if="props.obrigatorio" class="obrigatorio" aria-hidden="true"> *</span>
    </label>
    <slot :id="props.id" :descricao="props.erro ? `${props.id}-erro` : props.ajuda ? `${props.id}-ajuda` : undefined" :invalido="!!props.erro" />
    <p v-if="props.erro" :id="`${props.id}-erro`" class="erro" role="alert"><Icone nome="alerta" tamanho="sm" />{{ props.erro }}</p>
    <p v-else-if="props.ajuda" :id="`${props.id}-ajuda`" class="ajuda">{{ props.ajuda }}</p>
  </div>
</template>
