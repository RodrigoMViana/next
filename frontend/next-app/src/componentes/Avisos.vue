<script setup lang="ts">
import { avisos, fecharAviso } from '@/estado/avisos'
import Icone from './Icone.vue'
</script>

<template>
  <div class="toasts" aria-live="polite" aria-atomic="false">
    <div v-for="a in avisos" :key="a.id" class="toast" :class="a.tipo" :role="a.tipo === 'erro' ? 'alert' : 'status'">
      <Icone :nome="a.tipo === 'sucesso' ? 'ok' : 'alerta'" />
      <div class="grow">
        <strong>{{ a.texto }}</strong>
        <div v-if="a.detalhe" class="small mid">{{ a.detalhe }}</div>
      </div>
      <button type="button" class="btn-icon" aria-label="Fechar aviso" @click="fecharAviso(a.id)"><Icone nome="fechar" tamanho="sm" /></button>
    </div>
  </div>
</template>
