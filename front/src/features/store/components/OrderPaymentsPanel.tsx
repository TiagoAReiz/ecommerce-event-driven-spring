'use client'

import { useState } from 'react'
import { Button, Skeleton } from '@/components/ui'
import { money, dateTime } from '@/lib/format'
import { errorDescription } from '../errors'
import { useCancelPayment, usePaymentsByOrder, useSyncPayment } from '../queries'
import { PaymentStatusBadge } from './StatusBadges'
import { RefundModal } from './RefundModal'
import type { PaymentAttempt } from '../types'

/** Estorno so' faz sentido com dinheiro capturado (contrato: 422 fora disso). */
const REFUNDABLE = new Set(['authorized', 'captured'])
/** Cancelamento de cobranca so' antes de captar (contrato: 409 se ja captado). */
const CANCELLABLE = new Set(['pending', 'authorized'])

/** Lista as tentativas de pagamento de um pedido (`GET /payments?orderId=`) com
 * as acoes que a loja pode tomar sobre cada uma: sincronizar, cancelar cobranca
 * pendente ou estornar. Carrega so' quando o pedido esta expandido na lista. */
export function OrderPaymentsPanel({ orderId }: { orderId: number }) {
  const paymentsQuery = usePaymentsByOrder(orderId, true)
  const sync = useSyncPayment(orderId)
  const cancelPayment = useCancelPayment(orderId)
  const [refundTarget, setRefundTarget] = useState<PaymentAttempt | null>(null)

  if (paymentsQuery.isLoading) {
    return <Skeleton className="h-16 w-full" />
  }

  if (paymentsQuery.isError) {
    return <p className="text-sm text-muted">{errorDescription(paymentsQuery.error) ?? 'Não foi possível carregar os pagamentos.'}</p>
  }

  const payments = paymentsQuery.data ?? []

  if (payments.length === 0) {
    return <p className="text-sm text-muted">Nenhuma tentativa de pagamento ainda.</p>
  }

  return (
    <div className="flex flex-col gap-3">
      {payments.map((payment) => (
        <div key={payment.id} className="flex flex-col gap-2 rounded-[8px] border border-line p-3 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-medium text-ink">Pagamento #{payment.id}</span>
            <PaymentStatusBadge status={payment.status} />
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2 text-muted">
            <span>{money(payment.value)} · {payment.provider}{payment.method ? ` · ${payment.method}` : ''}</span>
            <span>{dateTime(payment.updatedAt)}</span>
          </div>
          {payment.statusDetail && <p className="text-xs text-muted">{payment.statusDetail}</p>}

          <div className="flex flex-wrap gap-2 pt-1">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              loading={sync.isPending && sync.variables === payment.id}
              onClick={() => sync.mutate(payment.id)}
            >
              Sincronizar
            </Button>
            {CANCELLABLE.has(payment.status) && (
              <Button
                type="button"
                variant="secondary"
                size="sm"
                loading={cancelPayment.isPending && cancelPayment.variables === payment.id}
                onClick={() => cancelPayment.mutate(payment.id)}
              >
                Cancelar cobrança
              </Button>
            )}
            {REFUNDABLE.has(payment.status) && (
              <Button type="button" variant="danger" size="sm" onClick={() => setRefundTarget(payment)}>
                Estornar
              </Button>
            )}
          </div>

          {sync.isError && sync.variables === payment.id && (
            <p className="text-xs text-rose-600">{errorDescription(sync.error)}</p>
          )}
          {cancelPayment.isError && cancelPayment.variables === payment.id && (
            <p className="text-xs text-rose-600">{errorDescription(cancelPayment.error)}</p>
          )}
        </div>
      ))}

      {refundTarget && (
        <RefundModal orderId={orderId} payment={refundTarget} onClose={() => setRefundTarget(null)} />
      )}
    </div>
  )
}
