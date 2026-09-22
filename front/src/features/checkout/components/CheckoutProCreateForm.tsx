'use client'

import { useState } from 'react'
import { Button } from '../../../components/ui'
import { ApiError, newIdempotencyKey } from '../../../lib/api'
import type { PaymentDetail } from '../../orders/types'
import { ErrorNote } from './ErrorNote'
import { useCreatePayment } from '../queries'

/** Checkout Pro: so cria a preference; o redirecionamento acontece com `detail.initPoint`. */
export function CheckoutProCreateForm({
  idOrder,
  defaultEmail,
  onCreated,
}: {
  idOrder: number
  defaultEmail: string
  onCreated: (payment: PaymentDetail) => void
}) {
  const [idempotencyKey] = useState(() => newIdempotencyKey())
  const createPayment = useCreatePayment()

  function submit() {
    if (createPayment.isPending) return
    createPayment.mutate({ body: { idOrder, method: 'checkout_pro' }, idempotencyKey }, { onSuccess: onCreated })
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted">
        Você vai ser levado ao Mercado Pago para escolher a forma de pagamento, com o e-mail{' '}
        <span className="text-ink">{defaultEmail}</span>.
      </p>

      {createPayment.error instanceof ApiError && <ErrorNote>{createPayment.error.message}</ErrorNote>}

      <Button loading={createPayment.isPending} onClick={submit}>
        Pagar com Checkout Pro
      </Button>
    </div>
  )
}
