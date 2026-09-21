import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { Button, Card, EmptyState, ErrorState, PageHeader, Skeleton } from '../../../components/ui'
import { ApiError } from '../../../lib/api'
import { dateTime, money } from '../../../lib/format'
import { CancelOrderModal } from '../components/CancelOrderModal'
import { ConfirmDeliveryModal } from '../components/ConfirmDeliveryModal'
import { OrderStatusBadge, PaymentStatusBadge, ShipmentStatusBadge } from '../components/StatusBadge'
import { useOrder, usePaymentDetail, useShipmentDetail } from '../queries'

/** Status em que o comprador ainda pode pedir cancelamento (processing e' so' do owner). */
const CANCELLABLE_STATUSES = new Set(['pending', 'paid'])

/** Status de envio em que o comprador ja pode confirmar o recebimento. */
const CONFIRMABLE_SHIPMENT_STATUSES = new Set(['in_transit', 'out_for_delivery'])

export default function OrderDetailPage() {
  const params = useParams<{ id: string }>()
  const id = Number(params.id)
  const validId = Number.isFinite(id) && id > 0

  const [showCancel, setShowCancel] = useState(false)
  const [showConfirmDelivery, setShowConfirmDelivery] = useState(false)

  const orderQuery = useOrder(validId ? id : Number.NaN)

  if (!validId) {
    return (
      <>
        <PageHeader title="Pedido" />
        <EmptyState title="Pedido inválido" description="O endereço não aponta para um pedido válido." />
      </>
    )
  }

  if (orderQuery.isLoading) {
    return (
      <>
        <PageHeader title="Pedido" />
        <div className="flex flex-col gap-3">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
      </>
    )
  }

  if (orderQuery.isError) {
    const notFound = orderQuery.error instanceof ApiError && orderQuery.error.status === 404
    return (
      <>
        <PageHeader title="Pedido" />
        {notFound ? (
          <EmptyState title="Pedido não encontrado" description="Ele pode ser de outra conta ou não existir." />
        ) : (
          <ErrorState
            title="Não foi possível carregar o pedido"
            description={orderQuery.error instanceof ApiError ? orderQuery.error.message : undefined}
            onRetry={() => void orderQuery.refetch()}
          />
        )}
      </>
    )
  }

  const order = orderQuery.data
  if (!order) return null

  return (
    <>
      <PageHeader
        title={`Pedido #${order.id}`}
        description={`Feito em ${dateTime(order.createdAt)}`}
        action={<OrderStatusBadge status={order.status} />}
      />

      <div className="flex flex-col gap-4">
        <Card className="p-4">
          <h2 className="mb-3 text-sm font-semibold text-ink">Itens</h2>
          <div className="flex flex-col gap-3">
            {order.items.map((item) => (
              <div key={item.id} className="flex items-center gap-3">
                {item.productPhotoUrl ? (
                  <img src={item.productPhotoUrl} alt="" className="h-14 w-14 shrink-0 rounded-[8px] object-cover" />
                ) : (
                  <div className="h-14 w-14 shrink-0 rounded-[8px] bg-brand-50" />
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-ink">{item.productName}</p>
                  <p className="text-xs text-muted">
                    {item.quantity} × {money(item.priceAtTime)}
                  </p>
                </div>
                <p className="shrink-0 text-sm font-medium text-ink">{money(item.lineTotal)}</p>
              </div>
            ))}
          </div>

          <div className="mt-4 flex flex-col gap-1 border-t border-line pt-3 text-sm">
            <div className="flex justify-between text-muted">
              <span>Subtotal</span>
              <span>{money(order.itemsCost)}</span>
            </div>
            <div className="flex justify-between text-muted">
              <span>Frete</span>
              <span>{money(order.freightCost)}</span>
            </div>
            <div className="flex justify-between font-semibold text-ink">
              <span>Total</span>
              <span>{money(order.totalCost)}</span>
            </div>
          </div>
        </Card>

        <PaymentSection idPayment={order.payment?.id} fallbackStatus={order.payment?.status} />

        <ShipmentSection
          idShipment={order.shipment?.id}
          fallbackStatus={order.shipment?.status}
          fallbackTrackingCode={order.shipment?.trackingCode ?? null}
          onConfirmDelivery={() => setShowConfirmDelivery(true)}
        />

        {CANCELLABLE_STATUSES.has(order.status) && (
          <Card className="flex items-center justify-between gap-3 p-4">
            <p className="text-sm text-muted">Mudou de ideia? Você pode cancelar este pedido.</p>
            <Button variant="danger" size="sm" onClick={() => setShowCancel(true)}>
              Cancelar pedido
            </Button>
          </Card>
        )}
      </div>

      {showCancel && <CancelOrderModal orderId={order.id} onClose={() => setShowCancel(false)} />}
      {showConfirmDelivery && order.shipment && (
        <ConfirmDeliveryModal
          orderId={order.id}
          shipmentId={order.shipment.id}
          onClose={() => setShowConfirmDelivery(false)}
        />
      )}
    </>
  )
}

function PaymentSection({
  idPayment,
  fallbackStatus,
}: {
  idPayment: number | undefined
  fallbackStatus: string | undefined
}) {
  const paymentQuery = usePaymentDetail(idPayment)

  if (!idPayment) {
    return (
      <Card className="p-4">
        <h2 className="mb-2 text-sm font-semibold text-ink">Pagamento</h2>
        <p className="text-sm text-muted">Ainda não há pagamento registrado para este pedido.</p>
      </Card>
    )
  }

  const payment = paymentQuery.data

  return (
    <Card className="p-4">
      <h2 className="mb-2 text-sm font-semibold text-ink">Pagamento</h2>
      {paymentQuery.isLoading && <Skeleton className="h-16 w-full" />}
      {paymentQuery.isError && <p className="text-sm text-muted">Não foi possível carregar o pagamento agora.</p>}
      {payment && (
        <div className="flex flex-col gap-2 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-muted">Status</span>
            <PaymentStatusBadge status={payment.status} />
          </div>
          <div className="flex items-center justify-between">
            <span className="text-muted">Valor</span>
            <span className="font-medium text-ink">{money(payment.value)}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-muted">Forma</span>
            <span className="text-ink">{payment.method ?? '—'}</span>
          </div>
          {payment.detail?.ticketUrl && (
            <a
              href={payment.detail.ticketUrl}
              target="_blank"
              rel="noreferrer"
              className="text-brand-700 hover:underline"
            >
              Ver comprovante de pagamento
            </a>
          )}
          {payment.detail?.initPoint && (
            <a
              href={payment.detail.initPoint}
              target="_blank"
              rel="noreferrer"
              className="text-brand-700 hover:underline"
            >
              Continuar pagamento
            </a>
          )}
        </div>
      )}
      {!payment && !paymentQuery.isLoading && !paymentQuery.isError && fallbackStatus && (
        <p className="text-sm text-muted">Status: {fallbackStatus}</p>
      )}
    </Card>
  )
}

function ShipmentSection({
  idShipment,
  fallbackStatus,
  fallbackTrackingCode,
  onConfirmDelivery,
}: {
  idShipment: number | undefined
  fallbackStatus: string | undefined
  fallbackTrackingCode: string | null
  onConfirmDelivery: () => void
}) {
  const shipmentQuery = useShipmentDetail(idShipment)

  if (!idShipment) {
    return (
      <Card className="p-4">
        <h2 className="mb-2 text-sm font-semibold text-ink">Envio</h2>
        <p className="text-sm text-muted">O envio ainda não foi criado. Isso acontece assim que o estoque é baixado.</p>
      </Card>
    )
  }

  const shipment = shipmentQuery.data
  const trackingCode = shipment?.trackingCode ?? fallbackTrackingCode

  return (
    <Card className="p-4">
      <h2 className="mb-2 text-sm font-semibold text-ink">Envio</h2>
      {shipmentQuery.isLoading && <Skeleton className="h-16 w-full" />}
      {shipmentQuery.isError && <p className="text-sm text-muted">Não foi possível carregar o envio agora.</p>}

      {(shipment || fallbackStatus) && (
        <div className="flex flex-col gap-2 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-muted">Status</span>
            {shipment ? <ShipmentStatusBadge status={shipment.status} /> : <span className="text-ink">{fallbackStatus}</span>}
          </div>

          {trackingCode && (
            <div className="flex items-center justify-between">
              <span className="text-muted">Código de rastreio</span>
              <span className="font-mono text-ink">{trackingCode}</span>
            </div>
          )}

          {shipment?.destination && (
            <div className="flex items-center justify-between">
              <span className="text-muted">Destino</span>
              <span className="text-ink">
                {shipment.destination.city}/{shipment.destination.state} · {shipment.destination.zipcode}
              </span>
            </div>
          )}

          {shipment?.freightTax && (
            <div className="flex items-center justify-between">
              <span className="text-muted">Frete</span>
              <span className="text-ink">{money(shipment.freightTax)}</span>
            </div>
          )}

          {shipment && CONFIRMABLE_SHIPMENT_STATUSES.has(shipment.status) && (
            <div className="pt-2">
              <Button size="sm" onClick={onConfirmDelivery}>
                Confirmar entrega
              </Button>
            </div>
          )}
        </div>
      )}
    </Card>
  )
}
