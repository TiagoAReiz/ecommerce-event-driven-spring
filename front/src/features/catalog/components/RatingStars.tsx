import { cx } from '../../../lib/format'

/** Estrelas de avaliacao. Sem biblioteca de icones no projeto, entao e texto mesmo:
 * caractere unicode, acessivel via aria-label com o numero por extenso. */
export function RatingStars({
  rating,
  count,
  className,
}: {
  rating: string | number | null | undefined
  count?: number
  className?: string
}) {
  const value = rating === null || rating === undefined ? 0 : Number(rating)
  const rounded = Number.isNaN(value) ? 0 : Math.round(value)

  return (
    <span
      className={cx('inline-flex items-center gap-1 text-sm', className)}
      aria-label={`${Number.isNaN(value) ? 0 : value.toFixed(1)} de 5 estrelas${count !== undefined ? `, ${count} avaliacoes` : ''}`}
    >
      <span aria-hidden="true" className="tracking-tight text-brand-600">
        {'★★★★★'.slice(0, rounded)}
        <span className="text-line">{'★★★★★'.slice(rounded)}</span>
      </span>
      <span className="text-muted">{Number.isNaN(value) ? '—' : value.toFixed(1)}</span>
      {count !== undefined && <span className="text-muted">({count})</span>}
    </span>
  )
}
