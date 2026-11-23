<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Casca from '@/componentes/Casca.vue'
import Avisos from '@/componentes/Avisos.vue'

const route = useRoute()
const publica = computed(() => route.meta.publica === true)
</script>

<template>
  <RouterView v-if="publica" />
  <Casca v-else>
    <RouterView :key="route.fullPath" />
  </Casca>
  <Avisos />
</template>
