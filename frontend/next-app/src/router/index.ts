import { createRouter, createWebHashHistory } from 'vue-router'
import { carregarSessao, limparSessao, sessao, pode } from '@/estado/sessao'
import { quandoSessaoExpirar } from '@/api/cliente'

/**
 * Roteamento no cliente com hash (#/), o que permite servir o build estático pelo BFF, por proxy
 * reverso ou pelo GitHub Pages sem regras de reescrita. Telas carregadas sob demanda (code splitting).
 */
const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/entrar', component: () => import('@/views/EntrarView.vue'), meta: { publica: true, titulo: 'Entrar' } },
    { path: '/', component: () => import('@/views/InicioView.vue'), meta: { titulo: 'Configuração' } },
    { path: '/contratos', component: () => import('@/views/ContratosView.vue'), meta: { titulo: 'Contratos', permissao: 'lerCadastros' } },
    { path: '/contratos/novo', component: () => import('@/views/ContratoFormView.vue'), meta: { titulo: 'Novo contrato', permissao: 'escreverCadastros' } },
    { path: '/contratos/:id', component: () => import('@/views/ContratoView.vue'), meta: { titulo: 'Contrato', permissao: 'lerCadastros' } },
    { path: '/contratos/:id/editar', component: () => import('@/views/ContratoFormView.vue'), meta: { titulo: 'Editar contrato', permissao: 'escreverCadastros' } },
    { path: '/contratadas', component: () => import('@/views/ContratadasView.vue'), meta: { titulo: 'Contratadas', permissao: 'lerCadastros' } },
    { path: '/contratadas/:id', component: () => import('@/views/ContratadaView.vue'), meta: { titulo: 'Contratada', permissao: 'lerCadastros' } },
    { path: '/slas', component: () => import('@/views/SlasView.vue'), meta: { titulo: 'SLA', permissao: 'lerCadastros' } },
    { path: '/slas/novo', component: () => import('@/views/SlaFormView.vue'), meta: { titulo: 'Novo SLA', permissao: 'escreverCadastros' } },
    { path: '/slas/:id', component: () => import('@/views/SlaView.vue'), meta: { titulo: 'SLA', permissao: 'lerCadastros' } },
    { path: '/slas/:id/editar', component: () => import('@/views/SlaFormView.vue'), meta: { titulo: 'Editar SLA', permissao: 'escreverCadastros' } },
    { path: '/portfolio', component: () => import('@/views/PortfolioView.vue'), meta: { titulo: 'Portfólio', permissao: 'lerPortfolio' } },
    { path: '/servicos', component: () => import('@/views/CadastroApoioView.vue'), meta: { titulo: 'Serviços', permissao: 'lerCadastros', tipo: 'servicos' } },
    { path: '/unidades-medida', component: () => import('@/views/CadastroApoioView.vue'), meta: { titulo: 'Unidades de medida', permissao: 'lerCadastros', tipo: 'unidades-medida' } },
    { path: '/fontes-recurso', component: () => import('@/views/CadastroApoioView.vue'), meta: { titulo: 'Fontes de recursos', permissao: 'lerCadastros', tipo: 'fontes-recurso' } },
    { path: '/calendarios', component: () => import('@/views/CalendariosView.vue'), meta: { titulo: 'Calendários', permissao: 'lerCadastros' } },
    { path: '/calendarios/novo', component: () => import('@/views/CalendarioFormView.vue'), meta: { titulo: 'Novo calendário', permissao: 'escreverCadastros' } },
    { path: '/calendarios/:id', component: () => import('@/views/CalendarioFormView.vue'), meta: { titulo: 'Calendário', permissao: 'lerCadastros' } },
    { path: '/notificacoes', component: () => import('@/views/NotificacoesView.vue'), meta: { titulo: 'Notificações', permissao: 'notificacoes' } },
    { path: '/auditoria', component: () => import('@/views/AuditoriaView.vue'), meta: { titulo: 'Auditoria', permissao: 'auditoria' } },
    { path: '/sem-acesso', component: () => import('@/views/SemAcessoView.vue'), meta: { titulo: 'Sem acesso' } },
    { path: '/:caminho(.*)*', component: () => import('@/views/SemAcessoView.vue'), meta: { titulo: 'Página não encontrada', naoEncontrada: true } },
  ],
})

router.beforeEach(async (to) => {
  if (!sessao.carregada) await carregarSessao()
  if (to.meta.publica) return sessao.usuario && to.path === '/entrar' ? '/' : true
  if (!sessao.usuario) return { path: '/entrar', query: { voltar: to.fullPath } }
  if (!sessao.usuario.acessaConfiguracao && to.path !== '/sem-acesso') return '/sem-acesso'
  const permissao = to.meta.permissao as string | undefined
  if (permissao && !pode(permissao)) return '/sem-acesso'
  return true
})

router.afterEach((to) => {
  document.title = `${(to.meta.titulo as string) ?? 'Configuração'} · Next`
})

quandoSessaoExpirar(() => {
  limparSessao()
  router.push({ path: '/entrar', query: { expirada: '1' } })
})

export default router
