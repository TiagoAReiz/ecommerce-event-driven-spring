'use client'

import { useMemo } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, ErrorState, LinkButton, Skeleton } from '../../../components/ui'
import { ApiError, newIdempotencyKey } from '../../../lib/api'
import { money } from '../../../lib/format'
import { fetchShippingQuote } from '../../catalog/api'
import type { Address } from '../../account/types'
import { ErrorNote } from './ErrorNote'
import { addMoney } from '../lib/money'
import { cartKeys, useCreateOrder } from '../queries'
import type { Cart } from '../types'

/** Etapa 2: frete calculado a partir do endereco escolhido, revisao e confirmacao do pedido. */
export function ReviewStep({
  cart,
  address,
  onBack,
  onConfirmed,
}: {
  cart: Cart
  address: Address
  onBack: () => void
  onConfirmed: (orderId: number) => void
}) {
  const queryClient = useQueryClient()
  const quoteQuery = useQuery({
    queryKey: ['checkout', 'shipping-quote', address.zipcode],
    queryFn: () => fetchShippingQuote(address.zipcode),
  })
  const createOrder = useCreateOrder()

  const freightCost = quoteQuery.data?.freightCost
  const totalCost = freightCost ? addMoney(cart.itemsCost, freightCost) : undefined

  // Chave nova sempre que endereco ou total mudam (nova intencao); igual em retry do mesmo pedido.
  const idempotencyKey = useMemo(() => newIdempotencyKey(), [address.id, totalCost])

  function recalculate() {
    createOrder.reset()
    void queryClient.invalidateQueries({ queryKey: cartKeys.root })
    void quoteQuery.refetch()
  }

  function confirm() {
    if (!totalCost) return
    createOrder.mutate(
      { body: { addressId: address.id, expectedTotalCost: totalCost }, idempotencyKey },
      { onSuccess: (order) => onConfirmed(order.id) },
    )
  }

  const error = createOrder.error instanceof ApiError ? createOrder.error : null

  return (
    <div className="flex flex-col gap-4">
      <Card className="p-4">
        <h2 className="mb-2 text-sm font-semibold text-ink">Endereço de entrega</h2>
        <p className="text-sm text-ink">
          {address.street}
          {address.number ? `, ${address.number}` : ''}
        </p>
        <p className="text-sm text-muted">
          {address.city}/{address.state}
        </p>
        <Button variant="ghost" size="sm" className="mt-2" onClick={onBack}>
          Trocar endereço
        </Button>
      </Card>

      <Card className="p-4">
        <h2 className="mb-3 text-sm font-semibold text-ink">Itens</h2>
        <div className="flex flex-col gap-2">
          {cart.items.map((item) => (
            <div key={item.id} className="flex items-center justify-between text-sm">
              <span className="truncate text-ink">
                {item.quantity} × {item.product?.name ?? `Produto #${item.idProduct}`}
              </span>
              <span className="shrink-0 text-ink">{money(item.lineTotal)}</span>
            </div>
          ))}
        </div>

        <div className="mt-4 flex flex-col gap-1 border-t border-line pt-3 text-sm">
          <div className="flex justify-between text-muted">
            <span>Subtotal</span>
            <span>{money(cart.itemsCost)}</span>
          </div>
          <div className="flex justify-between text-muted">
            <span>Frete</span>
            {quoteQuery.isLoading ? <Skeleton className="h-4 w-16" /> : <span>{money(freightCost)}</span>}
          </div>
          <div className="flex justify-between font-semibold text-ink">
            <span>Total</span>
            <span>{totalCost ? money(totalCost) : '—'}</span>
          </div>
        </div>
      </Card>

      {quoteQuery.isError && (
        <ErrorState
          title="Não foi possível calcular o frete"
          description={quoteQuery.error instanceof ApiError ? quoteQuery.error.message : undefined}
          onRetry={() => void quoteQuery.refetch()}
        />
      )}

      {error && <OrderErrorPanel error={error} onRecalculate={recalculate} />}

      <Button loading={createOrder.isPending} disabled={!totalCost} onClick={confirm}>
        Confirmar pedido
      </Button>
    </div>
  )
}

function OrderErrorPanel({ error, onRecalculate }: { error: ApiError; onRecalculate: () => void }) {
  if (error.code === 'PRICE_CHANGED') {
    return (
      <ErrorNote>
        <p>O preço de algum item mudou desde que você abriu o carrinho.</p>
        <Button size="sm" className="mt-3" onClick={onRecalculate}>
          Recalcular
        </Button>
      </ErrorNote>
    )
  }

  if (error.code === 'INSUFFICIENT_STOCK') {
    return (
      <ErrorNote>
        <p>{error.problem.detail ?? 'Um dos itens não tem mais estoque suficiente.'}</p>
        <LinkButton to="/cart" size="sm" variant="secondary" className="mt-3">
          Voltar ao carrinho
        </LinkButton>
      </ErrorNote>
    )
  }

  if (error.code === 'IDEMPOTENCY_IN_FLIGHT') {
    return <ErrorNote>Seu pedido já está sendo processado. Aguarde um instante e tente de novo.</ErrorNote>
  }

  if (error.code === 'IDEMPOTENCY_KEY_REUSED') {
    return <ErrorNote>Algo mudou desde a última tentativa. Atualize a página e tente de novo.</ErrorNote>
  }

  return <ErrorNote>{error.message}</ErrorNote>
}
