<script setup lang="ts">
/**
 * Layout mobile first (Arquitetura 12.7): app bar de 48 px; no desktop sidebar de 56 px que o
 * hambúrguer expande para 240 px; no mobile bottom nav de até 5 itens.
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icone from './Icone.vue'
import { sessao, sair, pode } from '@/estado/sessao'
import { iniciais } from '@/lib/format'
import { MODO, restaurarDemonstracao } from '@/api'

const route = useRoute()
const router = useRouter()
const aberta = ref(false)
const mais = ref(false)
const menuUsuario = ref(false)

interface Item { para: string; rotulo: string; icone: string; visivel: boolean; grupo: string }
const itens = computed<Item[]>(() => [
  { para: '/', rotulo: 'Início', icone: 'inicio', visivel: true, grupo: '' },
  { para: '/contratos', rotulo: 'Contratos', icone: 'contrato', visivel: pode('lerCadastros'), grupo: 'Contratação' },
  { para: '/contratadas', rotulo: 'Contratadas', icone: 'empresa', visivel: pode('lerCadastros'), grupo: 'Contratação' },
  { para: '/slas', rotulo: 'SLA', icone: 'sla', visivel: pode('lerCadastros'), grupo: 'Contratação' },
  { para: '/portfolio', rotulo: 'Portfólio', icone: 'portfolio', visivel: pode('lerPortfolio'), grupo: 'Contratação' },
  { para: '/servicos', rotulo: 'Serviços', icone: 'servico', visivel: pode('lerCadastros'), grupo: 'Apoio' },
  { para: '/unidades-medida', rotulo: 'Unidades de medida', icone: 'regua', visivel: pode('lerCadastros'), grupo: 'Apoio' },
  { para: '/fontes-recurso', rotulo: 'Fontes de recursos', icone: 'carteira', visivel: pode('lerCadastros'), grupo: 'Apoio' },
  { para: '/calendarios', rotulo: 'Calendários', icone: 'calendario', visivel: pode('lerCadastros'), grupo: 'Apoio' },
  { para: '/notificacoes', rotulo: 'Notificações', icone: 'sino', visivel: pode('notificacoes'), grupo: 'Governança' },
  { para: '/auditoria', rotulo: 'Auditoria', icone: 'escudo', visivel: pode('auditoria'), grupo: 'Governança' },
].filter((i) => i.visivel))

const ativo = (para: string) => (para === '/' ? route.path === '/' : route.path.startsWith(para))
const principais = computed(() => itens.value.filter((i) => ['/', '/contratos', '/slas', '/contratadas'].includes(i.para)))
const secundarios = computed(() => itens.value.filter((i) => !principais.value.includes(i)))
const titulo = computed(() => (route.meta.titulo as string) ?? 'Configuração')

watch(() => route.fullPath, () => {
  mais.value = false
  aberta.value = false
  menuUsuario.value = false
})

async function sairDaSessao() {
  await sair()
  router.push('/entrar')
}

function restaurar() {
  if (confirm('Restaurar os dados fictícios de demonstração? As alterações feitas neste navegador serão perdidas.')) {
    restaurarDemonstracao()
    window.location.reload()
  }
}
</script>

<template>
  <a href="#conteudo" class="skip-link">Pular para o conteúdo</a>
  <header class="appbar">
    <button type="button" class="btn-icon" :aria-expanded="aberta" aria-controls="menu-lateral" aria-label="Abrir ou fechar o menu" @click="aberta = !aberta">
      <Icone nome="menu" />
    </button>
    <RouterLink to="/" class="brand"><span class="brand-mark" aria-hidden="true">N</span><span>Next</span></RouterLink>
    <span class="appbar-title" aria-hidden="true">· {{ titulo }}</span>
    <span class="spacer" />
    <span v-if="MODO === 'demo'" class="demo-flag" title="Dados fictícios, guardados apenas neste navegador">Demonstração</span>
    <div class="row">
      <button type="button" class="btn-icon" :aria-expanded="menuUsuario" aria-haspopup="true" :aria-label="`Usuário ${sessao.usuario?.nome}`" @click="menuUsuario = !menuUsuario">
        <span class="avatar" aria-hidden="true">{{ iniciais(sessao.usuario?.nome ?? '?') }}</span>
      </button>
    </div>
  </header>

  <div v-if="menuUsuario" class="card menu-usuario" role="menu">
    <strong>{{ sessao.usuario?.nome }}</strong>
    <div class="small mid">CPF {{ sessao.usuario?.cpfMascarado }}</div>
    <div class="small mid mb-3">{{ sessao.usuario?.perfis.join(', ') }}</div>
    <button v-if="MODO === 'demo'" type="button" class="btn btn-secondary mb-2" role="menuitem" @click="restaurar"><Icone nome="atualizar" />Restaurar dados fictícios</button>
    <button type="button" class="btn btn-secondary" role="menuitem" @click="sairDaSessao"><Icone nome="sair" />Sair</button>
  </div>

  <nav id="menu-lateral" class="sidebar" :class="{ aberta }" aria-label="Menu principal">
    <template v-for="(i, idx) in itens" :key="i.para">
      <div v-if="i.grupo && i.grupo !== itens[idx - 1]?.grupo" class="grupo">{{ i.grupo }}</div>
      <RouterLink :to="i.para" :class="{ ativo: ativo(i.para) }" :aria-current="ativo(i.para) ? 'page' : undefined" :title="i.rotulo">
        <Icone :nome="i.icone" /><span class="rotulo">{{ i.rotulo }}</span>
      </RouterLink>
    </template>
  </nav>

  <main id="conteudo" class="content" tabindex="-1">
    <div class="content-inner"><slot /></div>
  </main>

  <div v-if="mais" class="menu-mais" role="menu">
    <RouterLink v-for="i in secundarios" :key="i.para" :to="i.para" role="menuitem" :class="{ ativo: ativo(i.para) }"><Icone :nome="i.icone" />{{ i.rotulo }}</RouterLink>
  </div>
  <nav class="bottomnav" aria-label="Navegação principal">
    <RouterLink v-for="i in principais" :key="i.para" :to="i.para" :class="{ ativo: ativo(i.para) }" :aria-current="ativo(i.para) ? 'page' : undefined">
      <Icone :nome="i.icone" /><span>{{ i.rotulo }}</span>
    </RouterLink>
    <button v-if="secundarios.length" type="button" :class="{ ativo: mais }" :aria-expanded="mais" @click="mais = !mais"><Icone nome="reticencias" /><span>Mais</span></button>
  </nav>
</template>

<style scoped>
.menu-usuario { position: fixed; right: var(--nx-space-3); top: calc(var(--nx-appbar-h) + var(--nx-space-1)); z-index: var(--nx-z-dropdown);
  box-shadow: var(--nx-shadow-1); display: flex; flex-direction: column; min-width: 260px; }
</style>
