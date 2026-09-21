/** Formatadores compartilhados. Dinheiro nunca vira number: soma de centavos e do servidor. */

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function money(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  const asNumber = typeof value === 'number' ? value : Number(value)
  return Number.isNaN(asNumber) ? '—' : BRL.format(asNumber)
}

const DATE = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' })
const DATE_TIME = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

export function date(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '—' : DATE.format(parsed)
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '—' : DATE_TIME.format(parsed)
}

/** CEP so viaja com 8 digitos e sem mascara: o contrato recusa o resto. */
export function onlyDigits(value: string): string {
  return value.replace(/\D/g, '')
}

export function formatZipcode(value: string): string {
  const digits = onlyDigits(value).slice(0, 8)
  return digits.length > 5 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits
}

/** Junta classes ignorando o que for falso, sem trazer biblioteca para isso. */
export function cx(...values: (string | false | null | undefined)[]): string {
  return values.filter(Boolean).join(' ')
}
