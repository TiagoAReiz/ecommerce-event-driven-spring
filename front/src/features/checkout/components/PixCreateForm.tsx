import { useState } from 'react'
import { Button, Field, Input } from '../../../components/ui'
import { ApiError, newIdempotencyKey } from '../../../lib/api'
import { onlyDigits } from '../../../lib/format'
import { isValidCpfDigits } from '../../account/validation'
import type { PaymentDetail } from '../../orders/types'
import { ErrorNote } from './ErrorNote'
import { useCreatePayment } from '../queries'

/** Formulario de criacao do PIX: so email e CPF, o valor e sempre o do pedido no servidor. */
export function PixCreateForm({
  idOrder,
  defaultEmail,
  onCreated,
}: {
  idOrder: number
  defaultEmail: string
  onCreated: (payment: PaymentDetail) => void
}) {
  const [email, setEmail] = useState(defaultEmail)
  const [cpfDigits, setCpfDigits] = useState('')
  // Uma key por tentativa: continua a mesma em retry, muda se o form for remontado.
  const [idempotencyKey] = useState(() => newIdempotencyKey())
  const createPayment = useCreatePayment()

  const cpfValid = isValidCpfDigits(cpfDigits)
  const emailValid = /\S+@\S+\.\S+/.test(email)

  function submit() {
    if (!cpfValid || !emailValid || createPayment.isPending) return
    createPayment.mutate(
      {
        body: {
          idOrder,
          method: 'pix',
          payer: { email, identification: { type: 'CPF', number: cpfDigits } },
        },
        idempotencyKey,
      },
      { onSuccess: onCreated },
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <Field label="E-mail" required hint="Onde o Mercado Pago envia a confirmação.">
        <Input type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
      </Field>

      <Field label="CPF" required hint="Somente números, 11 dígitos.">
        <Input
          inputMode="numeric"
          value={cpfDigits}
          maxLength={11}
          onChange={(event) => setCpfDigits(onlyDigits(event.target.value).slice(0, 11))}
        />
      </Field>

      {createPayment.error instanceof ApiError && <ErrorNote>{createPayment.error.message}</ErrorNote>}

      <Button loading={createPayment.isPending} disabled={!cpfValid || !emailValid} onClick={submit}>
        Gerar QR code PIX
      </Button>
    </div>
  )
}
