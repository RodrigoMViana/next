/**
 * CNPJ numérico e alfanumérico (IN RFB nº 2.229/2024) — mesma regra do servidor (RN25 a RN28).
 * No cliente a validação é só para usabilidade: o servidor sempre revalida.
 */
export const PADRAO_CNPJ = /^[A-Z0-9]{12}[0-9]{2}$/

export function normalizarCnpj(entrada: string | null | undefined): string {
  return (entrada ?? '').trim().replace(/[./\-\s]/g, '').toUpperCase()
}

function digito(texto: string): number {
  let soma = 0
  let peso = 2
  for (let i = texto.length - 1; i >= 0; i--) {
    soma += (texto.charCodeAt(i) - 48) * peso
    peso = peso === 9 ? 2 : peso + 1
  }
  const resto = soma % 11
  return resto < 2 ? 0 : 11 - resto
}

export function calcularDigitosCnpj(base12: string): string {
  if (base12.length !== 12) throw new Error('A base do CNPJ deve ter 12 posições.')
  const dv1 = digito(base12)
  const dv2 = digito(base12 + dv1)
  return `${dv1}${dv2}`
}

/** Devolve a mensagem de erro do campo ou null quando válido. */
export function validarCnpj(entrada: string | null | undefined): string | null {
  const v = normalizarCnpj(entrada)
  if (!v) return 'Informe o CNPJ.'
  if (v.length !== 14) return 'O CNPJ deve ter 14 posições (sem contar a máscara).'
  if (!PADRAO_CNPJ.test(v)) {
    if (!/^[0-9]{2}$/.test(v.substring(12))) return 'Os dois dígitos verificadores do CNPJ devem ser numéricos.'
    return 'O CNPJ aceita apenas letras de A a Z e números de 0 a 9, sem acentos ou símbolos.'
  }
  if (v.split('').every((c) => c === v[0])) return 'CNPJ inválido: todos os caracteres são iguais.'
  if (calcularDigitosCnpj(v.substring(0, 12)) !== v.substring(12)) return 'Dígitos verificadores do CNPJ não conferem.'
  return null
}

export function formatarCnpj(v: string): string {
  const n = normalizarCnpj(v)
  if (n.length !== 14) return v
  return `${n.slice(0, 2)}.${n.slice(2, 5)}.${n.slice(5, 8)}/${n.slice(8, 12)}-${n.slice(12)}`
}

/** Máscara progressiva durante a digitação, aceitando letras nas 12 primeiras posições. */
export function mascararCnpj(entrada: string): string {
  const n = normalizarCnpj(entrada).replace(/[^A-Z0-9]/g, '').slice(0, 14)
  let r = n.slice(0, 2)
  if (n.length > 2) r += '.' + n.slice(2, 5)
  if (n.length > 5) r += '.' + n.slice(5, 8)
  if (n.length > 8) r += '/' + n.slice(8, 12)
  if (n.length > 12) r += '-' + n.slice(12, 14)
  return r
}
