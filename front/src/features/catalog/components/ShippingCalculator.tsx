'use client'

import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { Button, Field, Input } from '@/components/ui'
import { formatZipcode, money, onlyDigits } from '@/lib/format'
import { fetchShippingQuote } from '../api'

/** Calculadora de frete do detalhe do produto. So consulta quando o usuario manda
 * (nao chama a cada tecla): CEP so vale a pena calcular quando estiver completo. */
export function ShippingCalculator() {
  const [zipcode, setZipcode] = useState('')
  const [submitted, setSubmitted] = useState<string | null>(null)

  const digits = onlyDigits(zipcode)

  const quote = useQuery({
    queryKey: ['catalog', 'shipping-quote', submitted],
    queryFn: () => fetchShippingQuote(submitted as string),
    enabled: submitted !== null,
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (digits.length !== 8) return
    setSubmitted(digits)
  }

  return (
    <div className="rounded-[12px] border border-line p-4">
      <h2 className="mb-2 text-sm font-semibold text-ink">Calcular frete</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="flex-1">
          <Field label="CEP de destino" hint="8 dígitos, sem espaço">
            <Input
              value={formatZipcode(zipcode)}
              onChange={(event) => setZipcode(event.target.value)}
              inputMode="numeric"
              maxLength={9}
            />
          </Field>
        </div>
        <Button type="submit" variant="secondary" disabled={digits.length !== 8} loading={quote.isFetching}>
          Calcular
        </Button>
      </form>

      {quote.isSuccess && quote.data && (
        <p className="mt-3 text-sm text-ink">
          Frete para {quote.data.origin.city}/{quote.data.origin.state} até {formatZipcode(quote.data.zipcode)}:{' '}
          <span className="font-semibold">{money(quote.data.freightCost)}</span>{' '}
          <span className="text-muted">({quote.data.distanceKm} km)</span>
        </p>
      )}

      {quote.isError && (
        <p className="mt-3 text-sm text-rose-600">{shippingErrorMessage(quote.error)}</p>
      )}
    </div>
  )
}

function shippingErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 422) return 'CEP não encontrado. Confira o número e tente de novo.'
    if (error.status === 400) return 'CEP inválido: use 8 dígitos.'
    if (error.status === 429) return 'Muitas consultas de frete seguidas. Aguarde um instante.'
    if (error.status === 0) return 'Sem conexão com o servidor.'
    return 'Não foi possível calcular o frete agora. Tente novamente.'
  }
  return 'Não foi possível calcular o frete agora. Tente novamente.'
}
