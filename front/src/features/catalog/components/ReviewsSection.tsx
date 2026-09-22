'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { EmptyState, ErrorState, Pagination, Select, Skeleton } from '@/components/ui'
import { date } from '@/lib/format'
import { fetchReviews } from '../api'
import { errorDescription, errorTitle } from '../errors'
import { RatingStars } from './RatingStars'
import type { ReviewsResponse } from '../types'

/** Resumo (media + distribuicao por nota) e a lista paginada de avaliacoes do produto. */
export function ReviewsSection({
  productId,
  initialReviews,
}: {
  productId: string
  initialReviews?: ReviewsResponse
}) {
  const [rate, setRate] = useState<number | undefined>(undefined)
  const [page, setPage] = useState(0)

  // initialReviews so bate com a chave por que a rota servidor busca sem filtro de
  // nota e na primeira pagina; qualquer outro filtro precisa buscar de novo.
  const isInitialFilter = rate === undefined && page === 0

  const reviews = useQuery({
    queryKey: ['catalog', 'product', productId, 'reviews', rate ?? 'all', page],
    queryFn: () => fetchReviews(productId, { rate, page, size: 20, sort: 'createdAt,desc' }),
    initialData: isInitialFilter ? initialReviews : undefined,
  })

  function changeRate(value: string) {
    setRate(value ? Number(value) : undefined)
    setPage(0)
  }

  return (
    <section aria-labelledby="reviews-titulo">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 id="reviews-titulo" className="text-lg font-semibold text-ink">
          Avaliações
        </h2>
        <Select value={rate ?? ''} onChange={(event) => changeRate(event.target.value)} className="w-auto">
          <option value="">Todas as notas</option>
          <option value="5">5 estrelas</option>
          <option value="4">4 estrelas</option>
          <option value="3">3 estrelas</option>
          <option value="2">2 estrelas</option>
          <option value="1">1 estrela</option>
        </Select>
      </div>

      {reviews.isLoading && (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-16" />
          <Skeleton className="h-16" />
        </div>
      )}

      {reviews.isError && (
        <ErrorState
          title={errorTitle(reviews.error)}
          description={errorDescription(reviews.error)}
          onRetry={() => void reviews.refetch()}
        />
      )}

      {reviews.isSuccess && (
        <>
          <div className="mb-4 flex flex-wrap items-center gap-4">
            <RatingStars rating={reviews.data.summary.rating} count={reviews.data.summary.ratingCount} className="text-base" />
          </div>

          <DistributionBars distribution={reviews.data.summary.distribution} total={reviews.data.summary.ratingCount} />

          {reviews.data.content.length === 0 ? (
            <EmptyState title="Nenhuma avaliação com esse filtro" />
          ) : (
            <ul className="mt-4 flex flex-col gap-4">
              {reviews.data.content.map((review) => (
                <li key={review.id} className="rounded-[12px] border border-line p-4">
                  <div className="flex items-center gap-2">
                    {review.author.photoUrl ? (
                      <img src={review.author.photoUrl} alt="" className="h-8 w-8 rounded-full object-cover" />
                    ) : (
                      <span className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 text-xs font-medium text-brand-700">
                        {review.author.name.slice(0, 1).toUpperCase()}
                      </span>
                    )}
                    <div>
                      <p className="text-sm font-medium text-ink">{review.author.name}</p>
                      <p className="text-xs text-muted">{date(review.createdAt)}</p>
                    </div>
                  </div>
                  <RatingStars rating={review.rate} className="mt-2" />
                  <p className="mt-1 text-sm font-medium text-ink">{review.title}</p>
                  <p className="mt-1 text-sm text-muted">{review.description}</p>
                </li>
              ))}
            </ul>
          )}

          <Pagination page={page} totalPages={reviews.data.page.totalPages} onChange={setPage} />
        </>
      )}
    </section>
  )
}

function DistributionBars({ distribution, total }: { distribution: Record<string, number>; total: number }) {
  return (
    <div className="flex flex-col gap-1">
      {[5, 4, 3, 2, 1].map((star) => {
        const count = distribution[String(star)] ?? 0
        const pct = total > 0 ? Math.round((count / total) * 100) : 0
        return (
          <div key={star} className="flex items-center gap-2 text-xs text-muted">
            <span className="w-10 shrink-0">{star} estr.</span>
            <span className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
              <span className="block h-full rounded-full bg-brand-600" style={{ width: `${pct}%` }} />
            </span>
            <span className="w-10 shrink-0 text-right">{count}</span>
          </div>
        )
      })}
    </div>
  )
}
