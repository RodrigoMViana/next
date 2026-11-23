import { beforeEach, describe, expect, it } from 'vitest'
import { BffDemo } from '@/api/demo/bffDemo'
import { ProblemaError } from '@/api/cliente'
import type { ContratoDetalhe, Pagina, ContratoResumo, Painel, SlaResumo, SlaDetalhe } from '@/api/tipos'

const GESTOR = '10000000-0000-4000-8000-000000000002'
const N1 = '10000000-0000-4000-8000-000000000003'
const N2_ALFA = '10000000-0000-4000-8000-000000000004'
const N2_BETA = '10000000-0000-4000-8000-000000000006'
const CFG = '/api/v1/configuracao'

async function erro(p: Promise<unknown>): Promise<ProblemaError> {
  try {
    await p
  } catch (e) {
    return e as ProblemaError
  }
  throw new Error('esperava erro')
}

describe('BFF simulado (modo demonstração) aplica as mesmas regras do servidor', () => {
  let bff: BffDemo
  const login = (usuarioId: string, codigoMfa?: string) => bff.requisitar('POST', '/api/v1/auth/login', { corpo: { usuarioId, codigoMfa } })

  beforeEach(() => {
    localStorage.clear()
    bff = new BffDemo()
  })

  it('exige sessão e MFA para usuários de fábrica', async () => {
    expect((await erro(bff.requisitar('GET', `${CFG}/contratos`))).status).toBe(401)
    expect((await erro(login(N2_ALFA))).problema.type).toBe('urn:next:erro:mfa-obrigatorio')
    await login(N2_ALFA, '123456')
    const r = await bff.requisitar<Pagina<ContratoResumo>>('GET', `${CFG}/contratos`)
    expect(r.dados.content.every((c) => c.contratada.nome.includes('Alfa'))).toBe(true)
  })

  it('carga fictícia: painel, contratos com aditivo e SLA vigente', async () => {
    await login(GESTOR)
    const painel = (await bff.requisitar<Painel>('GET', `${CFG}/painel`)).dados
    expect(painel.totais.contratosAtivos).toBe(2)
    const lista = (await bff.requisitar<Pagina<ContratoResumo>>('GET', `${CFG}/contratos`, { query: { termo: 'CT-2025' } })).dados
    expect(lista.content[0]!.vigenciaFimEfetiva).toBe('2027-02-28')
    const slas = (await bff.requisitar<Pagina<SlaResumo>>('GET', `${CFG}/slas`, { query: { termo: 'SLA-ALFA' } })).dados
    expect(slas.content[0]!.versaoVigente).toBe(1)
  })

  it('RBAC: N1 não cadastra e não vê notificações', async () => {
    await login(N1)
    expect((await erro(bff.requisitar('POST', `${CFG}/contratadas`, { corpo: {} }))).status).toBe(403)
    expect((await erro(bff.requisitar('GET', `${CFG}/notificacoes`))).status).toBe(403)
  })

  it('contratada: CNPJ alfanumérico, unicidade e If-Match', async () => {
    await login(GESTOR)
    const e = await erro(bff.requisitar('POST', `${CFG}/contratadas`, { corpo: { cnpj: '12.ABC.345/01DE-36', razaoSocial: 'X' } }))
    expect(e.campos.cnpj).toBe('Dígitos verificadores do CNPJ não conferem.')
    const r = await bff.requisitar<{ id: string; cnpj: string }>('POST', `${CFG}/contratadas`, { corpo: { cnpj: 'a1.b2c.3d4/e5f6-68', razaoSocial: 'Gama' } })
    expect(r.dados.cnpj).toBe('A1B2C3D4E5F668')
    expect((await erro(bff.requisitar('POST', `${CFG}/contratadas`, { corpo: { cnpj: 'A1B2C3D4E5F668', razaoSocial: 'Dup' } }))).status).toBe(409)
    expect((await erro(bff.requisitar('PUT', `${CFG}/contratadas/${r.dados.id}`, { corpo: { cnpj: 'A1B2C3D4E5F668', razaoSocial: 'Gama SA' } }))).status).toBe(428)
    expect((await erro(bff.requisitar('PUT', `${CFG}/contratadas/${r.dados.id}`, { corpo: { cnpj: 'A1B2C3D4E5F668', razaoSocial: 'Gama SA' }, ifMatch: '"9"' }))).status).toBe(412)
    await bff.requisitar('PUT', `${CFG}/contratadas/${r.dados.id}`, { corpo: { cnpj: 'A1B2C3D4E5F668', razaoSocial: 'Gama SA' }, ifMatch: '"0"' })
  })

  it('contrato: aditivo preserva original e recalcula efetivos; RN06 impede ficar sem fiscal', async () => {
    await login(GESTOR)
    const lista = (await bff.requisitar<Pagina<ContratoResumo>>('GET', `${CFG}/contratos`, { query: { termo: 'CT-2026-0007' } })).dados
    const id = lista.content[0]!.id
    const antes = (await bff.requisitar<ContratoDetalhe>('GET', `${CFG}/contratos/${id}`)).dados
    const depois = (await bff.requisitar<ContratoDetalhe>('POST', `${CFG}/contratos/${id}/aditivos`, { corpo: { numero: '2º TA', aditivoPrazo: true, aditivoValor: true,
      novaVigenciaFim: '2027-12-31', valorAcrescimo: 1000, dataAssinatura: '2026-10-01', justificativa: 'Teste' } })).dados
    expect(depois.vigenciaFim).toBe(antes.vigenciaFim)
    expect(depois.vigenciaFimEfetiva).toBe('2027-12-31')
    expect(depois.valorEfetivo).toBeCloseTo(antes.valorEfetivo + 1000, 2)
    for (const f of depois.fiscais.slice(1)) await bff.requisitar('POST', `${CFG}/contratos/${id}/fiscais/${f.id}/encerramento`, { corpo: { fim: '2026-11-01', motivo: 'x' } })
    const unico = depois.fiscais[0]!
    const e = await erro(bff.requisitar('POST', `${CFG}/contratos/${id}/fiscais/${unico.id}/encerramento`, { corpo: { fim: '2026-11-01', motivo: 'x' } }))
    expect(e.problema.type).toBe('urn:next:erro:contrato-sem-fiscal')
  })

  it('SLA: parecer só da fábrica do contrato e vigência não retroativa', async () => {
    await login(GESTOR)
    const sla = (await bff.requisitar<Pagina<SlaResumo>>('GET', `${CFG}/slas`, { query: { termo: 'SLA-ALFA' } })).dados.content[0]!
    const v2 = (await bff.requisitar<{ id: string }>('POST', `${CFG}/slas/${sla.id}/versoes`, { corpo: { motivoAlteracao: 'Ajuste' } })).dados
    await bff.requisitar('POST', `${CFG}/slas/${sla.id}/versoes/${v2.id}/envio-avaliacao`, { corpo: {} })
    await login(N2_BETA, '123456')
    expect((await erro(bff.requisitar('POST', `${CFG}/slas/${sla.id}/versoes/${v2.id}/parecer`, { corpo: { favoravel: true, texto: 'ok' } }))).status).toBe(404)
    await login(N2_ALFA, '123456')
    await bff.requisitar('POST', `${CFG}/slas/${sla.id}/versoes/${v2.id}/parecer`, { corpo: { favoravel: true, texto: 'ok' } })
    await login(GESTOR)
    const e = await erro(bff.requisitar('POST', `${CFG}/slas/${sla.id}/versoes/${v2.id}/decisao`, { corpo: { aprovar: true, vigenciaInicio: '2020-01-01' } }))
    expect(e.campos.vigenciaInicio).toMatch(/retroativa/)
    const amanha = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10)
    await bff.requisitar('POST', `${CFG}/slas/${sla.id}/versoes/${v2.id}/decisao`, { corpo: { aprovar: true, vigenciaInicio: amanha } })
    const det = (await bff.requisitar<SlaDetalhe>('GET', `${CFG}/slas/${sla.id}`)).dados
    expect(det.versoes.map((v) => v.situacao)).toEqual(['VIGENTE', 'VIGENTE'])
  })

  it('notificação: e-mail sem CPF e limiar obrigatório no alerta de risco', async () => {
    await login(GESTOR)
    const base = { nome: 'x', canalApp: true, perfis: ['N1'], assunto: 'a' }
    expect((await erro(bff.requisitar('POST', `${CFG}/notificacoes`, { corpo: { ...base, evento: 'sustentacao.sla.violado', canalEmail: true, modelo: '{{usuario.cpf}}' } }))).campos.modelo).toBeTruthy()
    expect((await erro(bff.requisitar('POST', `${CFG}/notificacoes`, { corpo: { ...base, evento: 'sustentacao.sla.em-risco', canalEmail: false, modelo: '{{chamado.codigo}}' } }))).campos.limiarPercentual).toBeTruthy()
  })
})
