<script setup lang="ts">
import { useRoute } from 'vue-router'
import Icone from '@/componentes/Icone.vue'
import { sessao } from '@/estado/sessao'

const route = useRoute()
</script>

<template>
  <div class="card vazio">
    <div class="icone"><Icone :nome="route.meta.naoEncontrada ? 'busca' : 'cadeado'" tamanho="lg" /></div>
    <h1 class="mb-3">{{ route.meta.naoEncontrada ? 'Página não encontrada' : 'Sem acesso a esta funcionalidade' }}</h1>
    <p v-if="!route.meta.naoEncontrada">
      O perfil {{ sessao.usuario?.perfis.join(', ') }} não acessa esta parte do módulo Configuração
      (seção 5.4 do DOD: Facilitador, Responsável da Área, Membro de comitê, N2+ técnico e Cliente usam outros módulos).
    </p>
    <RouterLink v-if="sessao.usuario?.acessaConfiguracao" to="/" class="btn btn-secondary">Voltar ao início</RouterLink>
  </div>
</template>
