import { useEffect, useRef, useState } from 'react'
import { Button, Card, Spinner } from '../../../components/ui'
import { ApiError } from '../../../lib/api'
import { dateTime } from '../../../lib/format'
import { PaymentStatusBadge } from '../../orders/components/StatusBadge'
import type { PaymentDetail } from '../../orders/types'
import { ErrorNote } from './ErrorNote'
import { useSyncPayment } from '../queries'
import { translateRejection } from '../types'

/** Cooldown do lado do cliente: o contrato recusa mais de 1 sync por minuto no mesmo pagamento. */
const SYNC_COOLDOWN_MS = 60_000

/**
 * Acompanhamento de um pagamento ja criado. `PaymentPage` releva `GET /payments/{id}` a
 * cada 5s por fora (hook `usePaymentPolling`); aqui so decidimos o que mostrar por status
 * e modalidade, e oferecemos a reconciliacao manual (`sync`).
 */
export function PaymentTracker({ payment, onRetry }: { payment: PaymentDetail; onRetry: () => void }) {
  const syncPayment = useSyncPayment(payment.id)
  const [syncLocked, setSyncLocked] = useState(false)
  const unlockTimer = useRef<number | null>(null)

  useEffect(() => () => {
    if (unlockTimer.current) window.clearTimeout(unlockTimer.current)
  }, [])

  function requestSync() {
    if (syncLocked || syncPayment.isPending) return
    setSyncLocked(true)
    unlockTimer.current = window.setTimeout(() => setSyncLocked(false), SYNC_COOLDOWN_MS)
    syncPayment.mutate()
  }

  // Checkout Pro leva direto ao Mercado Pago assim que a preference existe.
  useEffect(() => {
    if (payment.method === 'checkout_pro' && payment.status === 'pending' && payment.detail?.initPoint) {
      window.location.href = payment.detail.initPoint
    }
  }, [payment.method, payment.status, payment.detail?.initPoint])

  if (payment.status === 'failed') {
    return (
      <Card className="flex flex-col gap-3 p-4">
        <p className="text-sm font-medium text-ink">Pagamento recusado</p>
        <ErrorNote>{translateRejection(payment.statusDetail)}</ErrorNote>
        <Button onClick={onRetry}>Tentar outra forma de pagamento</Button>
      </Card>
    )
  }

  if (payment.method === 'checkout_pro') {
    return (
      <Card className="flex flex-col items-center gap-3 p-6 text-center">
        <Spinner className="h-6 w-6 text-brand-700" />
        <p className="text-sm text-ink">Redirecionando para o Mercado Pago…</p>
        {payment.detail?.initPoint && (
          <a href={payment.detail.initPoint} className="text-sm text-brand-700 hover:underline">
            Clique aqui se a página não abrir sozinha
          </a>
        )}
      </Card>
    )
  }

  if (payment.method === 'pix') {
    const expired = Boolean(payment.detail?.expiresAt) && Date.now() > new Date(payment.detail!.expiresAt!).getTime()

    return (
      <Card className="flex flex-col gap-4 p-4">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-ink">Pagar com PIX</p>
          <PaymentStatusBadge status={payment.status} />
        </div>

        {expired ? (
          <div className="flex flex-col items-center gap-3 py-4 text-center">
            <p className="text-sm text-ink">Este QR code expirou.</p>
            <Button onClick={onRetry}>Gerar novo PIX</Button>
          </div>
        ) : (
          <>
            {payment.detail?.qrCodeBase64 && (
              <img
                src={`data:image/png;base64,${payment.detail.qrCodeBase64}`}
                alt="QR code do PIX"
                className="mx-auto h-56 w-56 rounded-[8px] border border-line"
              />
            )}

            {payment.detail?.qrCode && (
              <div className="flex flex-col gap-2">
                <p className="break-all rounded-[8px] border border-line bg-white px-3 py-2 text-xs text-muted">
                  {payment.detail.qrCode}
                </p>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => void navigator.clipboard.writeText(payment.detail!.qrCode!)}
                >
                  Copiar código
                </Button>
              </div>
            )}

            {payment.detail?.ticketUrl && (
              <a href={payment.detail.ticketUrl} target="_blank" rel="noreferrer" className="text-sm text-brand-700 hover:underline">
                Abrir no Mercado Pago
              </a>
            )}

            {payment.detail?.expiresAt && (
              <p className="text-xs text-muted">Expira em {dateTime(payment.detail.expiresAt)}</p>
            )}

            <p className="text-xs text-muted">Acompanhamos o pagamento automaticamente a cada poucos segundos.</p>

            <Button variant="secondary" size="sm" disabled={syncLocked} loading={syncPayment.isPending} onClick={requestSync}>
              {syncLocked ? 'Sincronizado há pouco' : 'Verificar agora'}
            </Button>

            {syncPayment.error instanceof ApiError && (
              <p className="text-xs text-muted">
                {syncPayment.error.status === 429
                  ? 'Muitas verificações seguidas. Aguarde um minuto.'
                  : syncPayment.error.message}
              </p>
            )}
          </>
        )}
      </Card>
    )
  }

  // Cartao pendente/autorizado (raro: normalmente resolve na hora) ou modalidade sem UI propria.
  return (
    <Card className="flex flex-col items-center gap-3 p-6 text-center">
      <Spinner className="h-6 w-6 text-brand-700" />
      <p className="text-sm text-ink">Aguardando confirmação do pagamento…</p>
      <PaymentStatusBadge status={payment.status} />
    </Card>
  )
}
