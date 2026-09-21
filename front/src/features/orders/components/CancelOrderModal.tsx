import { useState } from 'react'
import { ApiError } from '../../../lib/api'
import { Button, Field, Textarea } from '../../../components/ui'
import { Modal } from './Modal'
import { useCancelOrder } from '../queries'

const REASON_MAX = 500

/** Modal de cancelamento: motivo obrigatorio, ate 500 caracteres (limite do contrato). */
export function CancelOrderModal({ orderId, onClose }: { orderId: number; onClose: () => void }) {
  const [reason, setReason] = useState('')
  const cancelOrder = useCancelOrder(orderId)

  const overLimit = reason.length > REASON_MAX
  const empty = reason.trim().length === 0

  function submit() {
    if (empty || overLimit || cancelOrder.isPending) return
    cancelOrder.mutate(reason.trim(), { onSuccess: onClose })
  }

  const conflictMessage =
    cancelOrder.error instanceof ApiError && cancelOrder.error.status === 409
      ? cancelOrder.error.problem.detail ?? 'Este pedido não pode mais ser cancelado.'
      : null

  const genericMessage =
    cancelOrder.error instanceof ApiError && cancelOrder.error.status !== 409 ? cancelOrder.error.message : null

  return (
    <Modal title="Cancelar pedido" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted">
          Conte por que está cancelando. Se o pedido já estiver pago, o estorno acontece
          automaticamente assim que o pagamento confirmar o cancelamento.
        </p>

        <Field
          label="Motivo"
          required
          error={overLimit ? `Máximo de ${REASON_MAX} caracteres.` : undefined}
          hint={overLimit ? undefined : `${reason.length}/${REASON_MAX}`}
        >
          <Textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={4}
            aria-label="Motivo do cancelamento"
            maxLength={REASON_MAX + 50}
          />
        </Field>

        {conflictMessage && (
          <p className="rounded-[8px] bg-brand-50 px-3 py-2 text-sm text-ink">{conflictMessage}</p>
        )}
        {genericMessage && <p className="text-sm text-ink">{genericMessage}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={cancelOrder.isPending}>
            Voltar
          </Button>
          <Button variant="danger" onClick={submit} loading={cancelOrder.isPending} disabled={empty || overLimit}>
            Cancelar pedido
          </Button>
        </div>
      </div>
    </Modal>
  )
}
