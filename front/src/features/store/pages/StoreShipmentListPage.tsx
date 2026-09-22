'use client'

import { useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Button, EmptyState, ErrorState, Field, Input, PageHeader, Pagination, Select, Skeleton } from '@/components/ui'
import { dateTime, money } from '@/lib/format'
import { errorDescription, errorTitle } from '../errors'
import { useCancelShipment, useManageShipments, usePatchShipment } from '../queries'
import { ShipmentStatusBadge } from '../components/StatusBadges'
import { ReasonModal } from '../components/ReasonModal'
import type { ShipmentOwnerStatus } from '../types'
import type { ShipmentStatus } from '@/features/orders/types'

const PAGE_SIZE = 20

/** Proxima transicao que a loja pode pedir a partir de cada status (contrato §10.2).
 * `delivered` fica fora de proposito nesta tela inteira: e' so' do comprador. */
const NEXT_STATUSES: Partial<Record<ShipmentStatus, ShipmentOwnerStatus[]>> = {
  pending: ['ready_to_ship'],
  ready_to_ship: ['in_transit'],
  in_transit: ['out_for_delivery', 'returned'],
  out_for_delivery: ['returned'],
}

/** Status em que a loja ainda pode cancelar o envio (contrato §10.4). */
const CANCELLABLE_STATUSES = new Set<ShipmentStatus>(['pending', 'ready_to_ship'])

const TRANSITION_LABEL: Record<ShipmentOwnerStatus, string> = {
  ready_to_ship: 'Marcar pronto para envio',
  in_transit: 'Despachar (em trânsito)',
  out_for_delivery: 'Saiu para entrega',
  returned: 'Marcar devolvido',
}

const STATUS_OPTIONS: { value: ShipmentStatus | ''; label: string }[] = [
  { value: '', label: 'Todos os status' },
  { value: 'pending', label: 'Aguardando despacho' },
  { value: 'ready_to_ship', label: 'Pronto para envio' },
  { value: 'in_transit', label: 'Em trânsito' },
  { value: 'out_for_delivery', label: 'Saiu para entrega' },
  { value: 'delivered', label: 'Entregue' },
  { value: 'returned', label: 'Devolvido' },
  { value: 'cancelled', label: 'Cancelado' },
]

/** Fila de despacho e gestao de envios (`GET /shipments/manage`). As transicoes
 * oferecidas aqui sao so' as que o contrato deixa a loja pedir — `delivered` e'
 * do comprador e nunca aparece como botao nesta tela. */
export default function StoreShipmentListPage() {
  // useSearchParams do next/navigation e' somente leitura: mudar filtro/pagina
  // exige router.push com a query string nova, nao ha' um setSearchParams aqui.
  const searchParams = useSearchParams()
  const router = useRouter()
  const status = (searchParams.get('status') as ShipmentStatus | null) ?? undefined
  const page = Number(searchParams.get('page') ?? '0')

  const shipmentsQuery = useManageShipments({ status, page, size: PAGE_SIZE })
  const patchShipment = usePatchShipment()
  const cancelShipment = useCancelShipment()

  const [trackingDraft, setTrackingDraft] = useState<Record<number, string>>({})
  const [cancelTargetId, setCancelTargetId] = useState<number | null>(null)

  function updateParams(patch: Record<string, string | null>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(patch)) {
      if (value === null || value === '') next.delete(key)
      else next.set(key, value)
    }
    router.push(`/store/shipments?${next.toString()}`)
  }

  function applyTransition(shipmentId: number, nextStatus: ShipmentOwnerStatus) {
    const trackingCode = trackingDraft[shipmentId]?.trim()
    patchShipment.mutate({
      id: shipmentId,
      body: { status: nextStatus, trackingCode: trackingCode || undefined },
    })
  }

  return (
    <>
      <PageHeader title="Envios" description="Fila de despacho e transições até o envio sair para entrega." />

      <div className="mb-6 max-w-xs">
        <Field label="Status">
          <Select value={status ?? ''} onChange={(event) => updateParams({ status: event.target.value || null, page: null })}>
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {shipmentsQuery.isLoading && (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
      )}

      {shipmentsQuery.isError && (
        <ErrorState
          title={errorTitle(shipmentsQuery.error)}
          description={errorDescription(shipmentsQuery.error)}
          onRetry={() => void shipmentsQuery.refetch()}
        />
      )}

      {shipmentsQuery.data && shipmentsQuery.data.content.length === 0 && (
        <EmptyState title="Nenhum envio encontrado" description="Ajuste o filtro para ver outros envios." />
      )}

      {shipmentsQuery.data && shipmentsQuery.data.content.length > 0 && (
        <div className="flex flex-col gap-3">
          {shipmentsQuery.data.content.map((shipment) => {
            const nextOptions = NEXT_STATUSES[shipment.status] ?? []
            const canCancel = CANCELLABLE_STATUSES.has(shipment.status)
            const isPatching = patchShipment.isPending && patchShipment.variables?.id === shipment.id

            return (
              <div key={shipment.id} className="flex flex-col gap-3 rounded-[12px] border border-line bg-white p-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-ink">
                      Envio #{shipment.id} · pedido #{shipment.idOrder}
                    </p>
                    <p className="text-xs text-muted">
                      {shipment.destination.city}/{shipment.destination.state} · {shipment.destination.zipcode}
                    </p>
                    <p className="text-xs text-muted">
                      Frete {money(shipment.freightTax)} · atualizado em {dateTime(shipment.updatedAt)}
                    </p>
                    {shipment.trackingCode && (
                      <p className="text-xs text-muted">Código de rastreio: {shipment.trackingCode}</p>
                    )}
                  </div>
                  <ShipmentStatusBadge status={shipment.status} />
                </div>

                {nextOptions.length > 0 && (
                  <div className="flex flex-col gap-2 border-t border-line pt-3">
                    <Field label="Código de rastreio" hint="Opcional, texto livre, até 60 caracteres">
                      <Input
                        value={trackingDraft[shipment.id] ?? ''}
                        onChange={(event) =>
                          setTrackingDraft((prev) => ({ ...prev, [shipment.id]: event.target.value }))
                        }
                        maxLength={60}
                        aria-label={`Código de rastreio do envio ${shipment.id}`}
                      />
                    </Field>
                    <div className="flex flex-wrap gap-2">
                      {nextOptions.map((nextStatus) => (
                        <Button
                          key={nextStatus}
                          variant="secondary"
                          size="sm"
                          loading={isPatching && patchShipment.variables?.body.status === nextStatus}
                          onClick={() => applyTransition(shipment.id, nextStatus)}
                        >
                          {TRANSITION_LABEL[nextStatus]}
                        </Button>
                      ))}
                      {canCancel && (
                        <Button variant="danger" size="sm" onClick={() => setCancelTargetId(shipment.id)}>
                          Cancelar envio
                        </Button>
                      )}
                    </div>
                  </div>
                )}

                {!nextOptions.length && canCancel && (
                  <div className="border-t border-line pt-3">
                    <Button variant="danger" size="sm" onClick={() => setCancelTargetId(shipment.id)}>
                      Cancelar envio
                    </Button>
                  </div>
                )}

                {isPatching === false && patchShipment.isError && patchShipment.variables?.id === shipment.id && (
                  <p className="text-xs text-rose-600">{errorDescription(patchShipment.error)}</p>
                )}
              </div>
            )
          })}
        </div>
      )}

      {shipmentsQuery.data && (
        <Pagination
          page={shipmentsQuery.data.page.number}
          totalPages={shipmentsQuery.data.page.totalPages}
          onChange={(next) => updateParams({ page: next === 0 ? null : String(next) })}
        />
      )}

      {cancelTargetId !== null && (
        <ReasonModal
          title="Cancelar envio"
          description="Só é possível antes do despacho. Depois de 'em trânsito', o caminho é devolução."
          confirmLabel="Cancelar envio"
          danger
          isPending={cancelShipment.isPending}
          error={cancelShipment.error}
          onClose={() => setCancelTargetId(null)}
          onConfirm={(reason) =>
            cancelShipment.mutate(
              { id: cancelTargetId, body: { reason } },
              { onSuccess: () => setCancelTargetId(null) },
            )
          }
        />
      )}
    </>
  )
}
