import type { Money } from '../../../types/api'

/**
 * Soma dois valores em centavos, nao em float: `0.1 + 0.2` em ponto flutuante da errado,
 * e esse total vira `expectedTotalCost` comparado bit a bit pelo servidor.
 */
export function addMoney(a: Money, b: Money): Money {
  const cents = toCents(a) + toCents(b)
  return (cents / 100).toFixed(2)
}

function toCents(value: Money): number {
  const asNumber = Number(value)
  return Math.round((Number.isNaN(asNumber) ? 0 : asNumber) * 100)
}
