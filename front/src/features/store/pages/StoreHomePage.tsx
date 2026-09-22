'use client'

import Link from 'next/link'
import { Card, EmptyState, ErrorState, LinkButton, PageHeader, Skeleton } from '@/components/ui'
import { date, money } from '@/lib/format'
import { errorDescription, errorTitle } from '../errors'
import { useManageOrders, useManageShipments } from '../queries'
import { OrderStatusBadge, ShipmentStatusBadge } from '../components/StatusBadges'

const QUEUE_SIZE = 5

/** Painel de entrada da loja: fila de despacho, pedidos recentes e atalhos para
 * as demais telas. So' quem tem papel `owner` chega aqui (guarda no router). */
export default function StoreHomePage() {
  const pendingQuery = useManageShipments({ status: 'pending', page: 0, size: QUEUE_SIZE })
  const readyQuery = useManageShipments({ status: 'ready_to_ship', page: 0, size: QUEUE_SIZE })
  const recentOrdersQuery = useManageOrders({ page: 0, size: QUEUE_SIZE })

  const queueLoading = pendingQuery.isLoading || readyQuery.isLoading
  const queueError = pendingQuery.error ?? readyQuery.error
  const queueItems = [...(pendingQuery.data?.content ?? []), ...(readyQuery.data?.content ?? [])]
    .sort((a, b) => a.updatedAt.localeCompare(b.updatedAt))
    .slice(0, QUEUE_SIZE)
  const queueTotal = (pendingQuery.data?.page.totalElements ?? 0) + (readyQuery.data?.page.totalElements ?? 0)

  return (
    <>
      <PageHeader
        title="Área da loja"
        description="Painel de gestão: despacho, pedidos e catálogo."
        action={<LinkButton to="/store/products/new">Novo produto</LinkButton>}
      />

      <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <ShortcutCard to="/store/products" label="Produtos" />
        <ShortcutCard to="/store/orders" label="Pedidos" />
        <ShortcutCard to="/store/shipments" label="Envios" />
        <ShortcutCard to="/store/products/new" label="Novo produto" />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card className="p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-ink">Fila de despacho</h2>
            {!queueLoading && !queueError && <span className="text-xs text-muted">{queueTotal} no total</span>}
          </div>

          {queueLoading && (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </div>
          )}

          {!queueLoading && queueError && (
            <ErrorState title={errorTitle(queueError)} description={errorDescription(queueError)} />
          )}

          {!queueLoading && !queueError && queueItems.length === 0 && (
            <EmptyState title="Nada para despachar" description="Nenhum envio pendente neste momento." />
          )}

          {!queueLoading && !queueError && queueItems.length > 0 && (
            <ul className="flex flex-col gap-2">
              {queueItems.map((shipment) => (
                <li key={shipment.id}>
                  <Link
                    href="/store/shipments"
                    className="flex items-center justify-between gap-3 rounded-[8px] border border-line p-3 text-sm hover:border-brand-300"
                  >
                    <div>
                      <p className="font-medium text-ink">Pedido #{shipment.idOrder}</p>
                      <p className="text-xs text-muted">
                        {shipment.destination.city}/{shipment.destination.state}
                      </p>
                    </div>
                    <ShipmentStatusBadge status={shipment.status} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card className="p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-ink">Pedidos recentes</h2>
            <Link href="/store/orders" className="text-xs font-medium text-brand-700 hover:underline">
              Ver lista completa
            </Link>
          </div>

          {recentOrdersQuery.isLoading && (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </div>
          )}

          {recentOrdersQuery.isError && (
            <ErrorState
              title={errorTitle(recentOrdersQuery.error)}
              description={errorDescription(recentOrdersQuery.error)}
              onRetry={() => void recentOrdersQuery.refetch()}
            />
          )}

          {recentOrdersQuery.data && recentOrdersQuery.data.content.length === 0 && (
            <EmptyState title="Nenhum pedido ainda" description="Os pedidos da loja aparecem aqui assim que forem criados." />
          )}

          {recentOrdersQuery.data && recentOrdersQuery.data.content.length > 0 && (
            <ul className="flex flex-col gap-2">
              {recentOrdersQuery.data.content.map((order) => (
                <li key={order.id} className="flex items-center justify-between gap-3 rounded-[8px] border border-line p-3 text-sm">
                  <div>
                    <p className="font-medium text-ink">Pedido #{order.id}</p>
                    <p className="text-xs text-muted">{date(order.createdAt)} · {money(order.totalCost)}</p>
                  </div>
                  <OrderStatusBadge status={order.status} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  )
}

function ShortcutCard({ to, label }: { to: string; label: string }) {
  return (
    <Link
      href={to}
      className="flex items-center justify-center rounded-[12px] border border-line bg-white p-4 text-center text-sm font-medium text-ink hover:border-brand-300 hover:bg-brand-50"
    >
      {label}
    </Link>
  )
}
