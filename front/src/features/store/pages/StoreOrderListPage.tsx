'use client'

import { useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Button, EmptyState, ErrorState, Field, Input, PageHeader, Pagination, Select, Skeleton } from '@/components/ui'
import { date, money } from '@/lib/format'
import { errorDescription, errorTitle } from '../errors'
import { useCancelOrder, useManageOrders } from '../queries'
import { OrderStatusBadge } from '../components/StatusBadges'
import { OrderPaymentsPanel } from '../components/OrderPaymentsPanel'
import { ReasonModal } from '../components/ReasonModal'
import type { OrderStatus } from '@/features/orders/types'

const PAGE_SIZE = 20

/** Status em que a loja ainda pode cancelar o pedido (contrato §8.2). De `shipped`
 * em diante o caminho e' devolucao, nao cancelamento — por isso nao entram aqui. */
const CANCELLABLE_STATUSES = new Set<OrderStatus>(['pending', 'paid', 'processing'])

const STATUS_OPTIONS: { value: OrderStatus | ''; label: string }[] = [
  { value: '', label: 'Todos os status' },
  { value: 'pending', label: 'Aguardando pagamento' },
  { value: 'paid', label: 'Pago' },
  { value: 'processing', label: 'Em preparação' },
  { value: 'shipped', label: 'Enviado' },
  { value: 'delivered', label: 'Entregue' },
  { value: 'cancelled', label: 'Cancelado' },
  { value: 'refunded', label: 'Estornado' },
]

/** Pedidos da loja (`GET /orders/manage`), com cancelamento e, por pedido expandido,
 * as tentativas de pagamento com estorno — o contrato nao tem uma rota de pagamentos
 * por loja, so' por pedido, entao o painel de estorno mora aqui. */
export default function StoreOrderListPage() {
  // useSearchParams do next/navigation e' somente leitura: mudar filtro/pagina
  // exige router.push com a query string nova, nao ha' um setSearchParams aqui.
  const searchParams = useSearchParams()
  const router = useRouter()
  const status = (searchParams.get('status') as OrderStatus | null) ?? undefined
  const from = searchParams.get('from') ?? ''
  const to = searchParams.get('to') ?? ''
  const page = Number(searchParams.get('page') ?? '0')

  const ordersQuery = useManageOrders({ status, from: from || undefined, to: to || undefined, page, size: PAGE_SIZE })
  const cancelOrder = useCancelOrder()

  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [cancelTargetId, setCancelTargetId] = useState<number | null>(null)

  function updateParams(patch: Record<string, string | null>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(patch)) {
      if (value === null || value === '') next.delete(key)
      else next.set(key, value)
    }
    router.push(`/store/orders?${next.toString()}`)
  }

  return (
    <>
      <PageHeader title="Pedidos" description="Pedidos da loja inteira, com estorno e cancelamento." />

      <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Field label="Status">
          <Select value={status ?? ''} onChange={(event) => updateParams({ status: event.target.value || null, page: null })}>
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="De">
          <Input type="date" value={from} onChange={(event) => updateParams({ from: event.target.value || null, page: null })} />
        </Field>
        <Field label="Até">
          <Input type="date" value={to} onChange={(event) => updateParams({ to: event.target.value || null, page: null })} />
        </Field>
      </div>

      {ordersQuery.isLoading && (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      )}

      {ordersQuery.isError && (
        <ErrorState
          title={errorTitle(ordersQuery.error)}
          description={errorDescription(ordersQuery.error)}
          onRetry={() => void ordersQuery.refetch()}
        />
      )}

      {ordersQuery.data && ordersQuery.data.content.length === 0 && (
        <EmptyState title="Nenhum pedido encontrado" description="Ajuste os filtros para ver outros pedidos." />
      )}

      {ordersQuery.data && ordersQuery.data.content.length > 0 && (
        <div className="flex flex-col gap-3">
          {ordersQuery.data.content.map((order) => {
            const expanded = expandedId === order.id
            return (
              <div key={order.id} className="rounded-[12px] border border-line bg-white p-4">
                <button
                  type="button"
                  className="flex w-full flex-wrap items-center gap-4 text-left"
                  onClick={() => setExpandedId(expanded ? null : order.id)}
                >
                  {order.firstItem.productPhotoUrl ? (
                    <img
                      src={order.firstItem.productPhotoUrl}
                      alt=""
                      className="h-14 w-14 shrink-0 rounded-[8px] object-cover"
                    />
                  ) : (
                    <div className="h-14 w-14 shrink-0 rounded-[8px] bg-brand-50" />
                  )}

                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-ink">Pedido #{order.id} · cliente #{order.idCustomer}</p>
                    <p className="truncate text-xs text-muted">
                      {order.firstItem.productName}
                      {order.itemCount > 1 ? ` +${order.itemCount - 1} item(ns)` : ''} · {date(order.createdAt)}
                    </p>
                    <div className="mt-1">
                      <OrderStatusBadge status={order.status} />
                    </div>
                  </div>

                  <p className="shrink-0 text-sm font-semibold text-ink">{money(order.totalCost)}</p>
                </button>

                {expanded && (
                  <div className="mt-4 flex flex-col gap-4 border-t border-line pt-4">
                    <div className="flex flex-col gap-1 text-sm text-muted">
                      <div className="flex justify-between">
                        <span>Itens</span>
                        <span>{money(order.itemsCost)}</span>
                      </div>
                      <div className="flex justify-between">
                        <span>Frete</span>
                        <span>{money(order.freightCost)}</span>
                      </div>
                    </div>

                    <div>
                      <h3 className="mb-2 text-sm font-semibold text-ink">Pagamentos</h3>
                      <OrderPaymentsPanel orderId={order.id} />
                    </div>

                    {CANCELLABLE_STATUSES.has(order.status) && (
                      <div className="flex justify-end">
                        <Button variant="danger" size="sm" onClick={() => setCancelTargetId(order.id)}>
                          Cancelar pedido
                        </Button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {ordersQuery.data && (
        <Pagination
          page={ordersQuery.data.page.number}
          totalPages={ordersQuery.data.page.totalPages}
          onChange={(next) => updateParams({ page: next === 0 ? null : String(next) })}
        />
      )}

      {cancelTargetId !== null && (
        <ReasonModal
          title="Cancelar pedido"
          description="Isso encerra o pedido. Se já houver pagamento, o estorno acontece sozinho assim que o payment confirmar."
          confirmLabel="Cancelar pedido"
          danger
          isPending={cancelOrder.isPending}
          error={cancelOrder.error}
          onClose={() => setCancelTargetId(null)}
          onConfirm={(reason) =>
            cancelOrder.mutate(
              { id: cancelTargetId, body: { reason } },
              { onSuccess: () => setCancelTargetId(null) },
            )
          }
        />
      )}
    </>
  )
}
