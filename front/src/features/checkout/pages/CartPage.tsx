import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Card, EmptyState, ErrorState, LinkButton, PageHeader, Skeleton } from '../../../components/ui'
import { ApiError } from '../../../lib/api'
import { money } from '../../../lib/format'
import { CartIssueBadges } from '../components/CartIssueBadges'
import { CartItemRow } from '../components/CartItemRow'
import { EmptyCartModal } from '../components/EmptyCartModal'
import { useCart, useRemoveCartItem, useSetCartItemQuantity } from '../queries'
import { BLOCKING_CART_ISSUES } from '../types'

/** `/cart`: `GET /cart`, com `PUT`/`DELETE /cart/items/{id}` para ajustar. */
export default function CartPage() {
  const navigate = useNavigate()
  const cartQuery = useCart()
  const setQuantity = useSetCartItemQuantity()
  const removeItem = useRemoveCartItem()
  const [showEmptyModal, setShowEmptyModal] = useState(false)

  if (cartQuery.isLoading) {
    return (
      <>
        <PageHeader title="Carrinho" />
        <div className="flex flex-col gap-3">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      </>
    )
  }

  if (cartQuery.isError) {
    return (
      <>
        <PageHeader title="Carrinho" />
        <ErrorState
          title="Não deu para carregar seu carrinho"
          description={cartQuery.error instanceof ApiError ? cartQuery.error.message : undefined}
          onRetry={() => void cartQuery.refetch()}
        />
      </>
    )
  }

  const cart = cartQuery.data
  if (!cart) return null

  if (cart.items.length === 0) {
    return (
      <>
        <PageHeader title="Carrinho" />
        <EmptyState
          title="Seu carrinho está vazio"
          description="Adicione produtos da vitrine para continuar."
          action={<LinkButton to="/products">Ver produtos</LinkButton>}
        />
      </>
    )
  }

  const hasBlockingIssue = cart.items.some((item) => item.issues.some((issue) => BLOCKING_CART_ISSUES.includes(issue)))

  return (
    <>
      <PageHeader
        title="Carrinho"
        action={
          <Button variant="secondary" size="sm" onClick={() => setShowEmptyModal(true)}>
            Esvaziar carrinho
          </Button>
        }
      />

      {cart.issues.length > 0 && (
        <div className="mb-4">
          <CartIssueBadges issues={cart.issues} />
        </div>
      )}

      <div className="flex flex-col gap-3">
        {cart.items.map((item) => (
          <CartItemRow
            key={item.id}
            item={item}
            pending={setQuantity.isPending || removeItem.isPending}
            onQuantityChange={(quantity) => {
              if (quantity <= 0) {
                removeItem.mutate(item.idProduct)
                return
              }
              setQuantity.mutate({ idProduct: item.idProduct, quantity })
            }}
            onRemove={() => removeItem.mutate(item.idProduct)}
          />
        ))}
      </div>

      <Card className="mt-4 flex flex-col gap-3 p-4">
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted">Subtotal</span>
          <span className="font-semibold text-ink">{money(cart.itemsCost)}</span>
        </div>
        <p className="text-xs text-muted">O frete é calculado no checkout, a partir do endereço de entrega.</p>

        {hasBlockingIssue && (
          <p className="text-xs text-ink">
            Ajuste os itens marcados acima antes de continuar: indisponibilidade ou estoque insuficiente impedem o
            fechamento do pedido.
          </p>
        )}

        <Button disabled={hasBlockingIssue} onClick={() => navigate('/checkout')}>
          Ir para o checkout
        </Button>
      </Card>

      {showEmptyModal && <EmptyCartModal onClose={() => setShowEmptyModal(false)} />}
    </>
  )
}
