<script setup lang="ts">
/**
 * Entrada. Nesta fase: usuários fictícios por perfil (login de desenvolvimento) e MFA simulado para
 * usuários de fábrica. Integração pendente: gov.br (CPF) + SSO do Next via OIDC no BFF.
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, API, MODO } from '@/api'
import type { UsuarioDemo } from '@/api/tipos'
import { ProblemaError } from '@/api/cliente'
import { entrar } from '@/estado/sessao'
import Icone from '@/componentes/Icone.vue'
import Campo from '@/componentes/Campo.vue'
import { rotulo } from '@/lib/format'

const route = useRoute()
const router = useRouter()
const usuarios = ref<UsuarioDemo[]>([])
const escolhido = ref<UsuarioDemo | null>(null)
const codigo = ref('')
const erro = ref<string | null>(null)
const erroMfa = ref<string | null>(null)
const enviando = ref(false)

onMounted(async () => {
  try {
    usuarios.value = (await api.get<UsuarioDemo[]>(`${API}/auth/usuarios-demo`)).dados
  } catch (e) {
    erro.value = (e as Error).message
  }
})

async function confirmar() {
  if (!escolhido.value) return
  erro.value = null
  erroMfa.value = null
  enviando.value = true
  try {
    await entrar(escolhido.value.id, escolhido.value.exigeMfa ? codigo.value : undefined)
    router.replace((route.query.voltar as string) || '/')
  } catch (e) {
    if (e instanceof ProblemaError && e.campos.codigoMfa) erroMfa.value = e.campos.codigoMfa
    else erro.value = (e as Error).message
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <main class="login">
    <div class="card">
      <div class="row mb-3">
        <span class="brand-mark" aria-hidden="true">N</span>
        <h1>Next · Configuração</h1>
      </div>
      <div v-if="route.query.expirada" class="alerta alerta--aviso" role="status"><Icone nome="relogio" />Sua sessão expirou. Entre novamente.</div>
      <div class="alerta alerta--info">
        <Icone nome="info" />
        <div>
          <strong>Ambiente {{ MODO === 'demo' ? 'de demonstração' : 'de desenvolvimento' }} com dados fictícios.</strong>
          <div class="small">O acesso definitivo será pelo gov.br (CPF) e SSO do Next. Escolha um perfil para testar.</div>
        </div>
      </div>
      <div v-if="erro" class="alerta alerta--erro" role="alert"><Icone nome="alerta" />{{ erro }}</div>
      <form @submit.prevent="confirmar">
        <fieldset>
          <legend>Entrar como</legend>
          <div class="lista-usuarios">
            <button v-for="u in usuarios" :key="u.id" type="button" :aria-pressed="escolhido?.id === u.id" @click="(escolhido = u), (codigo = '')">
              <Icone nome="usuario" />
              <span class="grow">
                <strong>{{ u.nome }}</strong>
                <span class="small mid"> · {{ u.perfis.map(rotulo).join(', ') }}</span>
                <br /><span class="small subtle">{{ u.descricao }}</span>
              </span>
              <Icone v-if="u.exigeMfa" nome="cadeado" rotulo="Exige MFA" />
            </button>
          </div>
        </fieldset>
        <Campo v-if="escolhido?.exigeMfa" id="mfa" rotulo="Código de autenticação (MFA)" class="mt-4" :erro="erroMfa" obrigatorio
               ajuda="Usuários de fábrica exigem MFA. No ambiente de teste use 123456." v-slot="{ id, descricao, invalido }">
          <input :id="id" v-model="codigo" class="input" inputmode="numeric" autocomplete="one-time-code" maxlength="6"
                 :aria-describedby="descricao" :aria-invalid="invalido" />
        </Campo>
        <button type="submit" class="btn btn-primary mt-4" :disabled="!escolhido || enviando">Entrar</button>
      </form>
    </div>
  </main>
</template>
