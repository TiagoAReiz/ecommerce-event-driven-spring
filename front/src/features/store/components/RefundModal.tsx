'use client'

import { useState } from 'react'
import { Button, Field, Input, Textarea } from '@/components/ui'
import { ApiError } from '@/lib/api'
import { money } from '@/lib/format'
import { Modal } from './Modal'
import { useRefundPayment } from '../queries'
import type { PaymentAttempt } from '../types'

const REASON_MIN = 5
const REASON_MAX = 500

/** Estorno total ou parcial de um pagamento. `amount` pre-preenchido com o valor
 * cheio (contrato: omitir `amount` = estorno total; digitar menos = parcial). */
export function RefundModal({
  orderId,
  payment,
  onClose,
}: {
  orderId: number
  payment: PaymentAttempt
  onClose: () => void
}) {
  const [amount, setAmount] = useState(payment.value)
  const [reason, setReason] = useState('')
  const refund = useRefundPayment(orderId)

  const amountNumber = Number(amount)
  const totalNumber = Number(payment.value)
  const validAmount =
    /^\d+\.\d{2}$/.test(amount) && amountNumber > 0 && amountNumber <= totalNumber
  const reasonTrimmed = reason.trim()
  const validReason = reasonTrimmed.length >= REASON_MIN && reasonTrimmed.length <= REASON_MAX
  const isFull = amount === payment.value

  function submit() {
    if (!validAmount || !validReason || refund.isPending) return
    refund.mutate(
      { id: payment.id, body: { amount: isFull ? undefined : amount, reason: reasonTrimmed } },
      { onSuccess: onClose },
    )
  }

  const conflictMessage =
    refund.error instanceof ApiError && (refund.error.status === 409 || refund.error.status === 422)
      ? refund.error.problem.detail ?? 'Este pagamento não pode ser estornado agora.'
      : null
  const genericMessage = refund.error instanceof ApiError && !conflictMessage ? refund.error.message : null

  return (
    <Modal title={`Estornar pagamento #${payment.id}`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted">
          Valor pago: <span className="font-medium text-ink">{money(payment.value)}</span>. Deixe o valor
          cheio para estorno total ou reduza para um estorno parcial.
        </p>

        <Field
          label="Valor do estorno"
          required
          hint="Duas casas decimais, sem separador de milhar."
          error={amount && !validAmount ? 'Informe um valor válido, até o total pago.' : undefined}
        >
          <Input
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            inputMode="decimal"
            aria-label="Valor do estorno"
          />
        </Field>

        <Field
          label="Motivo"
          required
          hint={`${reason.length}/${REASON_MAX}`}
          error={reasonTrimmed && !validReason ? `Entre ${REASON_MIN} e ${REASON_MAX} caracteres.` : undefined}
        >
          <Textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            aria-label="Motivo do estorno"
            maxLength={REASON_MAX + 50}
          />
        </Field>

        {conflictMessage && <p className="rounded-[8px] bg-brand-50 px-3 py-2 text-sm text-ink">{conflictMessage}</p>}
        {genericMessage && <p className="text-sm text-ink">{genericMessage}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={refund.isPending}>
            Voltar
          </Button>
          <Button variant="danger" onClick={submit} loading={refund.isPending} disabled={!validAmount || !validReason}>
            Estornar
          </Button>
        </div>
      </div>
    </Modal>
  )
}
