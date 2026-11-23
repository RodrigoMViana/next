<script setup lang="ts">
/** Modal acessível: foco preso, Esc fecha, tela cheia no mobile (Arquitetura 12.8). */
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import Icone from './Icone.vue'

const props = defineProps<{ titulo: string; largo?: boolean }>()
const emit = defineEmits<{ fechar: [] }>()
const caixa = ref<HTMLElement | null>(null)
const idTitulo = `janela-${Math.random().toString(36).slice(2)}`
let anterior: Element | null = null

function focaveis(): HTMLElement[] {
  return Array.from(caixa.value?.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])') ?? [])
    .filter((e) => !e.hasAttribute('disabled'))
}

function teclado(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('fechar')
  if (e.key !== 'Tab') return
  const l = focaveis()
  if (!l.length) return
  const primeiro = l[0]!, ultimo = l[l.length - 1]!
  if (e.shiftKey && document.activeElement === primeiro) {
    e.preventDefault()
    ultimo.focus()
  } else if (!e.shiftKey && document.activeElement === ultimo) {
    e.preventDefault()
    primeiro.focus()
  }
}

onMounted(async () => {
  anterior = document.activeElement
  document.addEventListener('keydown', teclado)
  await nextTick()
  const l = focaveis()
  ;(l.find((e) => e.tagName !== 'BUTTON') ?? l[0])?.focus()
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', teclado)
  ;(anterior as HTMLElement | null)?.focus?.()
})
</script>

<template>
  <div class="modal-fundo" @click.self="emit('fechar')">
    <div ref="caixa" class="modal" :class="{ largo: props.largo }" role="dialog" aria-modal="true" :aria-labelledby="idTitulo">
      <div class="modal-head">
        <h2 :id="idTitulo">{{ props.titulo }}</h2>
        <button type="button" class="btn-icon" aria-label="Fechar" @click="emit('fechar')"><Icone nome="fechar" /></button>
      </div>
      <slot />
      <div v-if="$slots.acoes" class="modal-acoes"><slot name="acoes" /></div>
    </div>
  </div>
</template>
