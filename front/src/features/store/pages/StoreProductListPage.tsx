import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button, EmptyState, ErrorState, Field, Input, LinkButton, PageHeader, Pagination, Select, Skeleton } from '../../../components/ui'
import { money } from '../../../lib/format'
import { errorDescription, errorTitle } from '../errors'
import { useDeleteProduct, useManageProducts } from '../queries'
import { ProductStatusBadge } from '../components/StatusBadges'
import type { ProductManageStatus } from '../types'

const PAGE_SIZE = 20

const STATUS_OPTIONS: { value: ProductManageStatus; label: string }[] = [
  { value: 'active', label: 'Ativos' },
  { value: 'out_of_stock', label: 'Sem estoque' },
  { value: 'deleted', label: 'Removidos' },
  { value: 'all', label: 'Todos' },
]

/** Lista de gestao do catalogo (`GET /products/manage`): busca, filtro de status,
 * paginacao e exclusao. Criacao e edicao ficam em `StoreProductFormPage`. */
export default function StoreProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = (searchParams.get('status') as ProductManageStatus | null) ?? 'active'
  const q = searchParams.get('q') ?? ''
  const page = Number(searchParams.get('page') ?? '0')

  const [qDraft, setQDraft] = useState(q)
  useEffect(() => setQDraft(q), [q])
  useEffect(() => {
    const timer = window.setTimeout(() => {
      const trimmed = qDraft.trim()
      if (trimmed === q) return
      updateParams({ q: trimmed || null, page: null })
    }, 300)
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [qDraft])

  const productsQuery = useManageProducts({ status, q: q || undefined, page, size: PAGE_SIZE })
  const deleteProduct = useDeleteProduct()
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)

  function updateParams(patch: Record<string, string | number | null>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(patch)) {
      if (value === null || value === '') next.delete(key)
      else next.set(key, String(value))
    }
    setSearchParams(next)
  }

  function confirmDelete(id: number) {
    if (confirmDeleteId !== id) {
      setConfirmDeleteId(id)
      return
    }
    deleteProduct.mutate(id, { onSuccess: () => setConfirmDeleteId(null) })
  }

  return (
    <>
      <PageHeader
        title="Produtos"
        description="Catálogo completo da loja, com estoque bruto e itens removidos."
        action={<LinkButton to="/store/products/new">Novo produto</LinkButton>}
      />

      <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Field label="Buscar" hint="Nome ou descrição">
          <Input value={qDraft} onChange={(event) => setQDraft(event.target.value)} type="search" />
        </Field>
        <Field label="Status">
          <Select value={status} onChange={(event) => updateParams({ status: event.target.value, page: null })}>
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {productsQuery.isLoading && (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      )}

      {productsQuery.isError && (
        <ErrorState
          title={errorTitle(productsQuery.error)}
          description={errorDescription(productsQuery.error)}
          onRetry={() => void productsQuery.refetch()}
        />
      )}

      {productsQuery.data && productsQuery.data.content.length === 0 && (
        <EmptyState title="Nenhum produto encontrado" description="Ajuste os filtros ou cadastre o primeiro produto." />
      )}

      {productsQuery.data && productsQuery.data.content.length > 0 && (
        <div className="flex flex-col gap-3">
          {productsQuery.data.content.map((product) => (
            <div key={product.id} className="flex flex-wrap items-center gap-4 rounded-[12px] border border-line bg-white p-4">
              {product.photoUrl ? (
                <img src={product.photoUrl} alt="" className="h-16 w-16 shrink-0 rounded-[8px] object-cover" />
              ) : (
                <div className="h-16 w-16 shrink-0 rounded-[8px] bg-brand-50" />
              )}

              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-ink">{product.name}</p>
                <p className="text-xs text-muted">{product.category.name}</p>
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  <ProductStatusBadge product={product} />
                  <span className="text-xs text-muted">
                    Estoque {product.stock} · Disponível {product.available}
                  </span>
                </div>
              </div>

              <p className="shrink-0 text-sm font-semibold text-ink">{money(product.price)}</p>

              <div className="flex shrink-0 gap-2">
                <LinkButton to={`/store/products/${product.id}`} variant="secondary" size="sm">
                  Editar
                </LinkButton>
                {!product.deletedAt && (
                  <Button
                    variant="danger"
                    size="sm"
                    loading={deleteProduct.isPending && deleteProduct.variables === product.id}
                    onClick={() => confirmDelete(product.id)}
                  >
                    {confirmDeleteId === product.id ? 'Confirmar' : 'Excluir'}
                  </Button>
                )}
              </div>

              {deleteProduct.isError && deleteProduct.variables === product.id && (
                <p className="w-full text-xs text-rose-600">{errorDescription(deleteProduct.error)}</p>
              )}
            </div>
          ))}
        </div>
      )}

      {productsQuery.data && (
        <Pagination
          page={productsQuery.data.page.number}
          totalPages={productsQuery.data.page.totalPages}
          onChange={(next) => updateParams({ page: next === 0 ? null : next })}
        />
      )}
    </>
  )
}
