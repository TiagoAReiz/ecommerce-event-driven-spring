'use client'

import { useState } from 'react'
import { Button, Card, EmptyState, ErrorState, PageHeader, Pagination, Skeleton } from '@/components/ui'
import { ApiError } from '@/lib/api'
import { date } from '@/lib/format'
import { ReviewFormModal } from '../components/ReviewFormModal'
import type { ReviewFormValues } from '../components/ReviewFormModal'
import { useDeleteReview, useMyReviews, useUpdateReview } from '../queries'
import type { MyReview } from '../types'

/** Traduz o erro de `PATCH /reviews/{id}` para o comprador. */
function updateReviewError(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null
  if (error.status === 409) return error.problem.detail ?? 'A janela de 30 dias para editar esta avaliação já passou.'
  if (error.status === 422) return error.problem.detail ?? 'O texto não passou pela moderação.'
  if (error.status === 404) return 'Esta avaliação não existe mais.'
  return error.message
}

/** Avaliações do comprador: editar (dentro de 30 dias) ou excluir. */
export default function MyReviewsPage() {
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<MyReview | null>(null)

  const myReviewsQuery = useMyReviews(page)
  const updateReview = useUpdateReview(editing?.id ?? 0)
  const deleteReview = useDeleteReview()

  function submit(values: ReviewFormValues) {
    if (!editing) return
    updateReview.mutate(
      { rate: values.rate, title: values.title || undefined, description: values.description || undefined },
      { onSuccess: () => setEditing(null) },
    )
  }

  function remove(review: MyReview) {
    if (!window.confirm('Excluir esta avaliação? Essa ação não pode ser desfeita.')) return
    deleteReview.mutate(review.id)
  }

  return (
    <>
      <PageHeader title="Minhas avaliações" description="Edite em até 30 dias após publicar, ou exclua quando quiser." />

      {myReviewsQuery.isLoading && (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
        </div>
      )}

      {myReviewsQuery.isError && (
        <ErrorState
          title="Não foi possível carregar suas avaliações"
          description={myReviewsQuery.error instanceof ApiError ? myReviewsQuery.error.message : undefined}
          onRetry={() => void myReviewsQuery.refetch()}
        />
      )}

      {myReviewsQuery.data && myReviewsQuery.data.content.length === 0 && (
        <EmptyState title="Você ainda não avaliou nada" description="Suas avaliações publicadas aparecem aqui." />
      )}

      {myReviewsQuery.data && myReviewsQuery.data.content.length > 0 && (
        <div className="flex flex-col gap-3">
          {myReviewsQuery.data.content.map((review) => (
            <Card key={review.id} className="flex gap-3 p-4">
              {review.productPhotoUrl ? (
                <img src={review.productPhotoUrl} alt="" className="h-16 w-16 shrink-0 rounded-[8px] object-cover" />
              ) : (
                <div className="h-16 w-16 shrink-0 rounded-[8px] bg-brand-50" />
              )}

              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-ink">{review.productName}</p>
                <p className="text-sm text-brand-700" aria-label={`Nota ${review.rate} de 5`}>
                  {'★'.repeat(review.rate)}
                  {'☆'.repeat(5 - review.rate)}
                </p>
                {review.title && <p className="mt-1 text-sm font-medium text-ink">{review.title}</p>}
                {review.description && <p className="text-sm text-muted">{review.description}</p>}
                <p className="mt-1 text-xs text-muted">
                  Avaliado em {date(review.createdAt)}
                  {review.editedAt ? ` · editado em ${date(review.editedAt)}` : ''}
                </p>

                <div className="mt-2 flex gap-2">
                  <Button variant="secondary" size="sm" onClick={() => setEditing(review)}>
                    Editar
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => remove(review)}
                    loading={deleteReview.isPending && deleteReview.variables === review.id}
                  >
                    Excluir
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {myReviewsQuery.data && (
        <Pagination page={myReviewsQuery.data.page.number} totalPages={myReviewsQuery.data.page.totalPages} onChange={setPage} />
      )}

      {editing && (
        <ReviewFormModal
          title="Editar avaliação"
          productName={editing.productName}
          submitLabel="Salvar alterações"
          initialValues={{ rate: editing.rate, title: editing.title ?? '', description: editing.description ?? '' }}
          isPending={updateReview.isPending}
          errorMessage={updateReviewError(updateReview.error)}
          fieldErrors={updateReview.error instanceof ApiError ? updateReview.error.fieldErrors : undefined}
          onSubmit={submit}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  )
}
