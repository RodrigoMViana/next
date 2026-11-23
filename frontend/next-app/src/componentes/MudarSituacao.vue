<script setup lang="ts">
/** Desativação/reativação com justificativa (RN04, RN08, RN15, RF05). Nada é excluído fisicamente. */
import { ref } from 'vue'
import { api } from '@/api'
import { ProblemaError } from '@/api/cliente'
import { avisar } from '@/estado/avisos'
import Janela from './Janela.vue'
import Campo from './Campo.vue'

const props = defineProps<{ caminho: string; ativo: boolean; nome: string; justificativaObrigatoria?: boolean }>()
const emit = defineEmits<{ fechar: []; concluido: [] }>()
const justificativa = ref('')
const erro = ref<string | null>(null)
const enviando = ref(false)

async function confirmar() {
  erro.value = null
  enviando.value = true
  try {
    await api.post(`${props.caminho}/${props.ativo ? 'desativacao' : 'reativacao'}`, { justificativa: justificativa.value || null })
    avisar(props.ativo ? `${props.nome} desativado.` : `${props.nome} reativado.`)
    emit('concluido')
  } catch (e) {
    erro.value = e instanceof ProblemaError ? (e.campos.justificativa ?? e.message) : (e as Error).message
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <Janela :titulo="props.ativo ? `Desativar ${props.nome}` : `Reativar ${props.nome}`" @fechar="emit('fechar')">
    <p class="mid">
      {{ props.ativo ? 'O registro não é excluído: fica com situação Desativado e o histórico é preservado.' : 'O registro volta a ficar disponível para uso.' }}
    </p>
    <Campo id="justificativa" rotulo="Justificativa" :erro="erro" :obrigatorio="props.justificativaObrigatoria"
           ajuda="Obrigatória quando o registro está em uso." v-slot="{ id, descricao, invalido }">
      <textarea :id="id" v-model="justificativa" class="input" maxlength="500" :aria-describedby="descricao" :aria-invalid="invalido" />
    </Campo>
    <template #acoes>
      <button type="button" class="btn btn-secondary" @click="emit('fechar')">Cancelar</button>
      <button type="button" class="btn" :class="props.ativo ? 'btn-danger' : 'btn-primary'" :disabled="enviando" @click="confirmar">
        {{ props.ativo ? 'Desativar' : 'Reativar' }}
      </button>
    </template>
  </Janela>
</template>
