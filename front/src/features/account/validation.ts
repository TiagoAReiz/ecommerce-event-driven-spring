/** Validacoes locais: pegam o erro obvio antes do POST, o resto e o backend quem valida. */

/** UFs do Brasil, para o Select do formulario de endereco. */
export const BRAZIL_STATES = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG',
  'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
] as const

/** O contrato exige exatamente 8 digitos, sem mascara. */
export function isValidZipcodeDigits(digits: string): boolean {
  return /^\d{8}$/.test(digits)
}

/** E.164: `+` seguido do codigo de pais e do numero, sem espaco ou traco. */
export function isValidPhone(value: string): boolean {
  return /^\+[1-9]\d{7,14}$/.test(value)
}

/** So a contagem de digitos: o digito verificador fica a cargo do backend. */
export function isValidCpfDigits(digits: string): boolean {
  return /^\d{11}$/.test(digits)
}
