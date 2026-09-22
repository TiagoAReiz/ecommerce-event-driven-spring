'use client'

import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { Card, EmptyState, ErrorState, Pagination, PageHeader, Select, Skeleton } from '@/components/ui'
import { ApiError } from '@/lib/api'
import { date, money } from '@/lib/format'
import { useOrders } from '../queries'
import { OrderStatusBadge } from '../components/StatusBadge'
import type { OrderStatus } from '../types'

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

/** Lista de pedidos do comprador, com filtro por status e paginação. */
export default function OrderListPage() {
  // useSearchParams do next/navigation e' somente leitura: mudar filtro/pagina
  // exige router.push com a query string nova, nao ha' um setSearchParams aqui.
  const searchParams = useSearchParams()
  const router = useRouter()
  const status = (searchParams.get('status') as OrderStatus | null) ?? undefined
  const page = Number(searchParams.get('page') ?? '0')

  const ordersQuery = useOrders({ status, page })

  function setStatus(value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set('status', value)
    else next.delete('status')
    next.delete('page')
    router.push(`/orders?${next.toString()}`)
  }

  function setPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    router.push(`/orders?${next.toString()}`)
  }

  return (
    <>
      <PageHeader title="Meus pedidos" description="Acompanhe pagamento, envio e entrega de cada compra." />

      <div className="mb-4 max-w-xs">
        <Select value={status ?? ''} onChange={(event) => setStatus(event.target.value)} aria-label="Filtrar por status">
          {STATUS_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
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
          title="Não foi possível carregar seus pedidos"
          description={ordersQuery.error instanceof ApiError ? ordersQuery.error.message : undefined}
          onRetry={() => void ordersQuery.refetch()}
        />
      )}

      {ordersQuery.data && ordersQuery.data.content.length === 0 && (
        <EmptyState
          title="Nenhum pedido por aqui"
          description="Quando você comprar algo, ele aparece nesta lista."
          action={
            <Link href="/products" className="text-sm font-medium text-brand-700 hover:underline">
              Ver produtos
            </Link>
          }
        />
      )}

      {ordersQuery.data && ordersQuery.data.content.length > 0 && (
        <div className="flex flex-col gap-3">
          {ordersQuery.data.content.map((order) => (
            <Link key={order.id} href={`/orders/${order.id}`}>
              <Card className="flex items-center gap-4 p-4 hover:border-brand-300">
                {order.firstItem.productPhotoUrl ? (
                  <img
                    src={order.firstItem.productPhotoUrl}
                    alt=""
                    className="h-16 w-16 shrink-0 rounded-[8px] object-cover"
                  />
                ) : (
                  <div className="h-16 w-16 shrink-0 rounded-[8px] bg-brand-50" />
                )}

                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-ink">{order.firstItem.productName}</p>
                  <p className="text-xs text-muted">
                    {order.itemCount > 1 ? `+${order.itemCount - 1} item(ns) · ` : ''}
                    {date(order.createdAt)}
                  </p>
                  <div className="mt-1">
                    <OrderStatusBadge status={order.status} />
                  </div>
                </div>

                <p className="shrink-0 text-sm font-semibold text-ink">{money(order.totalCost)}</p>
              </Card>
            </Link>
          ))}
        </div>
      )}

      {ordersQuery.data && (
        <Pagination page={ordersQuery.data.page.number} totalPages={ordersQuery.data.page.totalPages} onChange={setPage} />
      )}
    </>
  )
}
