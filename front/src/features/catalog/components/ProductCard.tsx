import { Link } from 'react-router-dom'
import { money } from '../../../lib/format'
import { Badge, Card } from '../../../components/ui'
import type { ProductSummary } from '../types'
import { RatingStars } from './RatingStars'

/** Card de produto usado na home e na listagem. Um jeito so de mostrar produto na loja. */
export function ProductCard({ product }: { product: ProductSummary }) {
  return (
    <Link to={`/products/${product.id}`} className="block h-full">
      <Card className="flex h-full flex-col overflow-hidden transition-shadow hover:shadow-md">
        <div className="aspect-square w-full bg-brand-50">
          {product.photoUrl ? (
            <img
              src={product.photoUrl}
              alt={product.name}
              loading="lazy"
              className="h-full w-full object-cover"
            />
          ) : (
            <div className="flex h-full w-full items-center justify-center text-sm text-muted">
              Sem foto
            </div>
          )}
        </div>
        <div className="flex flex-1 flex-col gap-1.5 p-3">
          <span className="text-xs text-muted">{product.category.name}</span>
          <h3 className="line-clamp-2 text-sm font-medium text-ink">{product.name}</h3>
          <RatingStars rating={product.rating} count={product.ratingCount} />
          <div className="mt-auto flex items-center justify-between pt-1">
            <span className="text-base font-semibold text-ink">{money(product.price)}</span>
            {product.available <= 0 && <Badge tone="warning">Esgotado</Badge>}
          </div>
        </div>
      </Card>
    </Link>
  )
}
