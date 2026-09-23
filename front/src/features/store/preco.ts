/**
 * Preco no formato que o contrato exige: duas casas, ponto decimal.
 *
 * <p>Quem cadastra digita "349", "349,9" ou "1.299,90" -- e o backend recusa
 * qualquer coisa fora de `\d+\.\d{2}`. Formatar na saida do campo evita que a
 * pessoa descubra isso so no erro do formulario.
 */

/** Deixa o usuario digitar numero, virgula e ponto; o resto nao entra. */
export function limparPreco(valor: string): string {
  return valor.replace(/[^\d.,]/g, '')
}

/**
 * Fecha o valor em duas casas.
 *
 * <p>Vale para o jeito brasileiro (1.299,90) e para o jeito do contrato
 * (1299.90): o ultimo separador manda, e o que vier antes e separador de milhar.
 */
export function formatarPreco(valor: string): string {
  const limpo = limparPreco(valor).trim()
  if (!limpo) return ''

  const ultimaVirgula = limpo.lastIndexOf(',')
  const ultimoPonto = limpo.lastIndexOf('.')
  const corte = Math.max(ultimaVirgula, ultimoPonto)

  let inteiros: string
  let centavos: string
  if (corte === -1) {
    inteiros = limpo
    centavos = ''
  } else {
    inteiros = limpo.slice(0, corte)
    centavos = limpo.slice(corte + 1)
    // Separador com tres digitos depois e milhar, nao centavos: "1.299" e 1299.
    if (centavos.length === 3 && !/[.,]/.test(inteiros)) {
      inteiros = inteiros + centavos
      centavos = ''
    }
  }

  inteiros = inteiros.replace(/[.,]/g, '') || '0'
  centavos = centavos.replace(/[.,]/g, '').slice(0, 2).padEnd(2, '0')

  // Sem zeros a esquerda, mas "0" continua sendo "0".
  inteiros = inteiros.replace(/^0+(?=\d)/, '')

  return `${inteiros}.${centavos}`
}
