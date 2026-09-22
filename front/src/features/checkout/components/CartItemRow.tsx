'use client'

import { Button, Card } from '../../../components/ui'
import { money } from '../../../lib/format'
import { CartIssueBadges } from './CartIssueBadges'
import type { CartItem } from '../types'

export function CartItemRow({
  item,
  onQuantityChange,
  onRemove,
  pending,
}: {
  item: CartItem
  onQuantityChange: (quantity: number) => void
  onRemove: () => void
  pending: boolean
}) {
  const unavailable = item.issues.includes('PRODUCT_UNAVAILABLE')

  return (
    <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
      {item.product?.photoUrl ? (
        <img
          src={item.product.photoUrl}
          alt=""
          className="h-16 w-16 shrink-0 rounded-[8px] object-cover"
        />
      ) : (
        <div className="h-16 w-16 shrink-0 rounded-[8px] bg-brand-50" />
      )}

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-ink">{item.product?.name ?? `Produto #${item.idProduct}`}</p>
        <p className="mt-0.5 text-xs text-muted">
          {item.product ? money(item.product.price) : 'carregando preço…'}
        </p>
        <div className="mt-2">
          <CartIssueBadges issues={item.issues} />
        </div>
      </div>

      <div className="flex items-center gap-3 sm:flex-col sm:items-end">
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            aria-label="Diminuir quantidade"
            disabled={pending || unavailable || item.quantity <= 1}
            onClick={() => onQuantityChange(item.quantity - 1)}
          >
            −
          </Button>
          <span className="w-6 text-center text-sm text-ink">{item.quantity}</span>
          <Button
            variant="secondary"
            size="sm"
            aria-label="Aumentar quantidade"
            disabled={pending || unavailable}
            onClick={() => onQuantityChange(item.quantity + 1)}
          >
            +
          </Button>
        </div>
        <p className="text-sm font-semibold text-ink">{money(item.lineTotal)}</p>
        <Button variant="ghost" size="sm" disabled={pending} onClick={onRemove}>
          Remover
        </Button>
      </div>
    </Card>
  )
}
