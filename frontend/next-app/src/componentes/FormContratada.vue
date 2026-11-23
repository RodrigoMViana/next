<script setup lang="ts">
/** RF06: cadastro/alteração de contratada com CNPJ numérico ou alfanumérico, máscara e DV (RN25 a RN28). */
import { computed, reactive, ref } from 'vue'
import { api, CFG } from '@/api'
import { ProblemaError } from '@/api/cliente'
import type { Contratada } from '@/api/tipos'
import Janela from './Janela.vue'
import Campo from './Campo.vue'
import Icone from './Icone.vue'
import { avisar } from '@/estado/avisos'
import { mascararCnpj, normalizarCnpj, validarCnpj } from '@/lib/cnpj'

const props = defineProps<{ contratada?: Contratada | null; etag?: string }>()
const emit = defineEmits<{ fechar: []; salvo: [c: Contratada] }>()
const f = reactive({ cnpj: props.contratada?.cnpjFormatado ?? '', razaoSocial: props.contratada?.razaoSocial ?? '', nomeFantasia: props.contratada?.nomeFantasia ?? '' })
const erros = ref<Record<string, string>>({})
const tocado = ref(false)
const salvando = ref(false)

const erroCnpj = computed(() => (tocado.value ? validarCnpj(f.cnpj) : null))
const alfanumerico = computed(() => !validarCnpj(f.cnpj) && !/^\d+$/.test(normalizarCnpj(f.cnpj)))

function aoDigitar(e: Event) {
  const alvo = e.target as HTMLInputElement
  f.cnpj = mascararCnpj(alvo.value)
}

async function colar(e: ClipboardEvent) {
  const t = e.clipboardData?.getData('text')
  if (t) {
    e.preventDefault()
    f.cnpj = mascararCnpj(t)
    tocado.value = true
  }
}

async function salvar() {
  tocado.value = true
  erros.value = {}
  salvando.value = true
  try {
    const corpo = { cnpj: f.cnpj, razaoSocial: f.razaoSocial, nomeFantasia: f.nomeFantasia || null }
    const r = props.contratada
      ? await api.put<Contratada>(`${CFG}/contratadas/${props.contratada.id}`, corpo, props.etag)
      : await api.post<Contratada>(`${CFG}/contratadas`, corpo)
    avisar(props.contratada ? 'Contratada alterada.' : 'Contratada cadastrada.')
    emit('salvo', r.dados)
  } catch (e) {
    if (e instanceof ProblemaError) erros.value = { _: e.message, ...e.campos }
    else erros.value = { _: (e as Error).message }
  } finally {
    salvando.value = false
  }
}
</script>

<template>
  <Janela :titulo="props.contratada ? 'Editar contratada' : 'Nova contratada'" @fechar="emit('fechar')">
    <p v-if="erros._" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ erros._ }}</p>
    <form class="form-grid" novalidate @submit.prevent="salvar">
      <Campo id="cnpj" rotulo="CNPJ" classe="col-12" :erro="erros.cnpj || erroCnpj" obrigatorio
             ajuda="Numérico ou alfanumérico (IN RFB 2.229/2024). Pode colar com ou sem máscara." v-slot="{ id, descricao, invalido }">
        <input :id="id" :value="f.cnpj" class="input mono" autocomplete="off" spellcheck="false" maxlength="18" placeholder="XX.XXX.XXX/XXXX-XX"
               :aria-describedby="descricao" :aria-invalid="invalido" @input="aoDigitar" @blur="tocado = true" @paste="colar" />
      </Campo>
      <p v-if="alfanumerico" class="col-12 small"><span class="badge badge--accent"><Icone nome="info" tamanho="sm" />CNPJ alfanumérico válido</span></p>
      <Campo id="razao" rotulo="Razão social" classe="col-12" :erro="erros.razaoSocial" obrigatorio v-slot="{ id, descricao, invalido }">
        <input :id="id" v-model="f.razaoSocial" class="input" maxlength="200" :aria-describedby="descricao" :aria-invalid="invalido" /></Campo>
      <Campo id="fantasia" rotulo="Nome fantasia" classe="col-12" v-slot="{ id }">
        <input :id="id" v-model="f.nomeFantasia" class="input" maxlength="200" /></Campo>
      <button type="submit" class="sr-only">Salvar</button>
    </form>
    <template #acoes>
      <button type="button" class="btn btn-secondary" @click="emit('fechar')">Cancelar</button>
      <button type="button" class="btn btn-primary" :disabled="salvando" @click="salvar">Salvar</button>
    </template>
  </Janela>
</template>
