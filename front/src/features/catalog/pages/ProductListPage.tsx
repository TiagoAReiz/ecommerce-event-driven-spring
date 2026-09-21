import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button, EmptyState, ErrorState, Field, Input, Pagination, PageHeader, Select, Skeleton } from '../../../components/ui'
import { money } from '../../../lib/format'
import { fetchCategories, fetchProducts } from '../api'
import type { ProductListParams } from '../api'
import { errorDescription, errorTitle } from '../errors'
import { ProductCard } from '../components/ProductCard'

const PAGE_SIZE = 20

/** Le os filtros direto da URL: ela e a fonte da verdade, assim o link e compartilhavel
 * e o back/forward do navegador funciona sem estado escondido em componente. */
function readParams(searchParams: URLSearchParams): ProductListParams {
  const categoryId = searchParams.get('categoryId')
  const minPrice = searchParams.get('minPrice')
  const maxPrice = searchParams.get('maxPrice')
  const minRating = searchParams.get('minRating')
  const page = searchParams.get('page')
  const sort = searchParams.get('sort')
  const q = searchParams.get('q')

  return {
    q: q || undefined,
    categoryId: categoryId ? Number(categoryId) : undefined,
    minPrice: minPrice || undefined,
    maxPrice: maxPrice || undefined,
    minRating: minRating || undefined,
    inStock: searchParams.get('inStock') === 'true' ? true : undefined,
    page: page ? Number(page) : 0,
    size: PAGE_SIZE,
    sort: sort || 'createdAt,desc',
  }
}

export default function ProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const params = useMemo(() => readParams(searchParams), [searchParams])

  // Campo de busca vive em estado local e so escreve na URL depois do debounce:
  // uma requisicao por pausa de digitacao, nao uma por tecla.
  const [qDraft, setQDraft] = useState(params.q ?? '')
  useEffect(() => setQDraft(params.q ?? ''), [params.q])
  useEffect(() => {
    const timer = window.setTimeout(() => {
      const trimmed = qDraft.trim()
      if (trimmed === (params.q ?? '')) return
      updateParams({ q: trimmed || null, page: null })
    }, 300)
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [qDraft])

  const categories = useQuery({
    queryKey: ['catalog', 'categories'],
    queryFn: () => fetchCategories(false),
  })

  const products = useQuery({
    queryKey: ['catalog', 'products', searchParams.toString()],
    queryFn: () => fetchProducts(params),
  })

  function updateParams(patch: Record<string, string | number | boolean | null>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(patch)) {
      if (value === null || value === '' || value === undefined) next.delete(key)
      else next.set(key, String(value))
    }
    setSearchParams(next)
  }

  function clearFilters() {
    setSearchParams(new URLSearchParams())
    setQDraft('')
  }

  const hasFilters =
    Boolean(params.q) ||
    Boolean(params.categoryId) ||
    Boolean(params.minPrice) ||
    Boolean(params.maxPrice) ||
    Boolean(params.minRating) ||
    Boolean(params.inStock) ||
    (params.sort && params.sort !== 'createdAt,desc')

  return (
    <>
      <PageHeader title="Produtos" />

      <div className="mb-4">
        <Field label="Buscar" hint="Nome ou descrição do produto, mínimo 2 letras">
          <Input type="search" value={qDraft} onChange={(event) => setQDraft(event.target.value)} />
        </Field>
      </div>

      <div className="mb-6 grid grid-cols-1 gap-3 rounded-[12px] border border-line p-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Categoria">
          <Select
            value={params.categoryId ?? ''}
            onChange={(event) => updateParams({ categoryId: event.target.value || null, page: null })}
          >
            <option value="">Todas</option>
            {categories.data?.content.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name} ({category.productCount})
              </option>
            ))}
          </Select>
        </Field>

        <Field label="Ordenar por">
          <Select value={params.sort} onChange={(event) => updateParams({ sort: event.target.value, page: null })}>
            <option value="createdAt,desc">Mais recentes</option>
            <option value="price,asc">Menor preço</option>
            <option value="price,desc">Maior preço</option>
            <option value="rating,desc">Melhor avaliados</option>
            <option value="name,asc">Nome (A-Z)</option>
          </Select>
        </Field>

        <Field label="Preço mínimo">
          <Input
            key={`min-${params.minPrice ?? ''}`}
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            defaultValue={params.minPrice ?? ''}
            onBlur={(event) => updateParams({ minPrice: event.target.value || null, page: null })}
          />
        </Field>

        <Field label="Preço máximo">
          <Input
            key={`max-${params.maxPrice ?? ''}`}
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            defaultValue={params.maxPrice ?? ''}
            onBlur={(event) => updateParams({ maxPrice: event.target.value || null, page: null })}
          />
        </Field>

        <Field label="Nota mínima">
          <Select
            value={params.minRating ?? ''}
            onChange={(event) => updateParams({ minRating: event.target.value || null, page: null })}
          >
            <option value="">Qualquer</option>
            <option value="4">4 ou mais</option>
            <option value="3">3 ou mais</option>
            <option value="2">2 ou mais</option>
            <option value="1">1 ou mais</option>
          </Select>
        </Field>

        <label className="flex items-center gap-2 self-end pb-2.5 text-sm text-ink">
          <input
            type="checkbox"
            checked={Boolean(params.inStock)}
            onChange={(event) => updateParams({ inStock: event.target.checked ? true : null, page: null })}
            className="h-4 w-4 rounded border-line text-brand-700"
          />
          Só produtos disponíveis
        </label>

        {hasFilters && (
          <Button variant="ghost" size="sm" className="self-end" onClick={clearFilters}>
            Limpar filtros
          </Button>
        )}
      </div>

      <ResultsSection
        isLoading={products.isLoading}
        isError={products.isError}
        error={products.error}
        data={products.data}
        page={params.page ?? 0}
        onPageChange={(page) => updateParams({ page: page === 0 ? null : page })}
        onRetry={() => void products.refetch()}
      />
    </>
  )
}

function ResultsSection({
  isLoading,
  isError,
  error,
  data,
  page,
  onPageChange,
  onRetry,
}: {
  isLoading: boolean
  isError: boolean
  error: unknown
  data: Awaited<ReturnType<typeof fetchProducts>> | undefined
  page: number
  onPageChange: (page: number) => void
  onRetry: () => void
}) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        {Array.from({ length: 8 }).map((_, index) => (
          <Skeleton key={index} className="aspect-square" />
        ))}
      </div>
    )
  }

  if (isError) {
    return <ErrorState title={errorTitle(error)} description={errorDescription(error)} onRetry={onRetry} />
  }

  if (!data || data.content.length === 0) {
    return (
      <EmptyState
        title="Nenhum produto encontrado"
        description="Tente outros termos de busca ou remova alguns filtros."
      />
    )
  }

  return (
    <>
      <p className="mb-3 text-sm text-muted">{data.page.totalElements} produtos encontrados</p>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        {data.content.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>
      <Pagination page={page} totalPages={data.page.totalPages} onChange={onPageChange} />
      {data.facets.priceRange && (
        <p className="text-center text-xs text-muted">
          Faixa de preço no filtro atual: {money(data.facets.priceRange.min)} – {money(data.facets.priceRange.max)}
        </p>
      )}
    </>
  )
}
