'use client'

import { Card, EmptyState, ErrorState, LinkButton, PageHeader, Skeleton } from '../../../components/ui'
import { ApiError } from '../../../lib/api'
import { dateTime, money } from '../../../lib/format'
import { OrderStatusBadge } from '../../orders/components/StatusBadge'
import { useOrder } from '../../orders/queries'
import type { OrderStatus } from '../../orders/types'

/** Mensagem por status: a saga continua depois do `201`, isso nao e erro. */
const STATUS_MESSAGE: Partial<Record<OrderStatus, string>> = {
  pending: 'Estamos confirmando seu pagamento.',
  paid: 'Pagamento confirmado — separando seu pedido.',
  processing: 'Seu pedido está sendo preparado.',
  shipped: 'Seu pedido já foi enviado.',
  delivered: 'Seu pedido foi entregue.',
}

/** `/checkout/done/:orderId`: confirmacao depois do pagamento aceito.
 * `orderId` chega por prop, vindo do segmento dinamico da rota. */
export default function OrderPlacedPage({ orderId: orderIdParam }: { orderId: string }) {
  const orderId = Number(orderIdParam)
  const validOrderId = Number.isFinite(orderId) && orderId > 0

  const orderQuery = useOrder(validOrderId ? orderId : Number.NaN)

  if (!validOrderId) {
    return (
      <>
        <PageHeader title="Pedido confirmado" />
        <EmptyState title="Pedido inválido" description="O endereço não aponta para um pedido válido." />
      </>
    )
  }

  if (orderQuery.isLoading) {
    return (
      <>
        <PageHeader title="Pedido confirmado" />
        <Skeleton className="h-48 w-full" />
      </>
    )
  }

  if (orderQuery.isError) {
    const notFound = orderQuery.error instanceof ApiError && orderQuery.error.status === 404
    return (
      <>
        <PageHeader title="Pedido confirmado" />
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
      <PageHeader title="Pedido confirmado" description={`Feito em ${dateTime(order.createdAt)}`} />

      <Card className="flex flex-col items-center gap-3 p-8 text-center">
        <p className="text-lg font-semibold text-ink">Pedido #{order.id}</p>
        <OrderStatusBadge status={order.status} />
        <p className="text-sm text-muted">{STATUS_MESSAGE[order.status] ?? 'Acompanhe o andamento na página do pedido.'}</p>
        <p className="text-sm text-ink">Total {money(order.totalCost)}</p>

        <div className="mt-4 flex flex-wrap justify-center gap-3">
          <LinkButton to={`/orders/${order.id}`}>Ver pedido</LinkButton>
          <LinkButton to="/products" variant="secondary">
            Continuar comprando
          </LinkButton>
        </div>
      </Card>
    </>
  )
}
