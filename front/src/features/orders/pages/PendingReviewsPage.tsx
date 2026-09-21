import { useState } from 'react'
import { Card, EmptyState, ErrorState, PageHeader, Pagination, Skeleton } from '../../../components/ui'
import { ApiError } from '../../../lib/api'
import { date } from '../../../lib/format'
import { ReviewFormModal } from '../components/ReviewFormModal'
import type { ReviewFormValues } from '../components/ReviewFormModal'
import { useCreateReview, usePendingReviews } from '../queries'
import type { ReviewEligibility } from '../types'

/** Traduz o erro de `POST /products/{id}/reviews` para o comprador. */
function createReviewError(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null
  if (error.status === 403) return 'Você não tem direito de avaliar este produto neste pedido.'
  if (error.status === 409) return error.problem.detail ?? 'Você já avaliou este produto neste pedido.'
  if (error.status === 422) return error.problem.detail ?? 'O texto não passou pela moderação.'
  if (error.status === 429) return 'Muitas avaliações em pouco tempo. Tente novamente em instantes.'
  return error.message
}

/** Produtos que o comprador pode avaliar: `review_eligibility` sem `review` ainda. */
export default function PendingReviewsPage() {
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<ReviewEligibility | null>(null)

  const pendingQuery = usePendingReviews(page)
  const createReview = useCreateReview(selected?.idProduct ?? 0)

  function submit(values: ReviewFormValues) {
    if (!selected) return
    createReview.mutate(
      { idOrder: selected.idOrder, rate: values.rate, title: values.title || undefined, description: values.description || undefined },
      { onSuccess: () => setSelected(null) },
    )
  }

  return (
    <>
      <PageHeader title="Avaliar compras" description="Produtos entregues que você ainda pode avaliar." />

      {pendingQuery.isLoading && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      )}

      {pendingQuery.isError && (
        <ErrorState
          title="Não foi possível carregar suas pendências"
          description={pendingQuery.error instanceof ApiError ? pendingQuery.error.message : undefined}
          onRetry={() => void pendingQuery.refetch()}
        />
      )}

      {pendingQuery.data && pendingQuery.data.content.length === 0 && (
        <EmptyState
          title="Nada para avaliar agora"
          description="Assim que um pedido for entregue, o produto aparece aqui."
        />
      )}

      {pendingQuery.data && pendingQuery.data.content.length > 0 && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {pendingQuery.data.content.map((item) => (
            <button
              key={`${item.idOrder}-${item.idProduct}`}
              type="button"
              onClick={() => setSelected(item)}
              className="text-left"
            >
              <Card className="flex h-full flex-col gap-2 p-3 hover:border-brand-300">
                {item.productPhotoUrl ? (
                  <img src={item.productPhotoUrl} alt="" className="h-24 w-full rounded-[8px] object-cover" />
                ) : (
                  <div className="h-24 w-full rounded-[8px] bg-brand-50" />
                )}
                <p className="line-clamp-2 text-sm font-medium text-ink">{item.productName}</p>
                <p className="text-xs text-muted">Recebido em {date(item.grantedAt)}</p>
                <span className="mt-auto text-sm font-medium text-brand-700">Avaliar</span>
              </Card>
            </button>
          ))}
        </div>
      )}

      {pendingQuery.data && (
        <Pagination page={pendingQuery.data.page.number} totalPages={pendingQuery.data.page.totalPages} onChange={setPage} />
      )}

      {selected && (
        <ReviewFormModal
          title="Avaliar produto"
          productName={selected.productName}
          submitLabel="Publicar avaliação"
          isPending={createReview.isPending}
          errorMessage={createReviewError(createReview.error)}
          fieldErrors={createReview.error instanceof ApiError ? createReview.error.fieldErrors : undefined}
          onSubmit={submit}
          onClose={() => setSelected(null)}
        />
      )}
    </>
  )
}
