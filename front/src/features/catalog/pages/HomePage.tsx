import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ApiError } from '../../../lib/api'
import { Card, EmptyState, ErrorState, PageHeader, Skeleton } from '../../../components/ui'
import { fetchCategories, fetchProducts } from '../api'
import { ProductCard } from '../components/ProductCard'
import type { ProductSummary } from '../types'

/** Vitrine: categorias para navegar e destaques (mais bem avaliados) para puxar o clique.
 * Nao existe endpoint de "aleatorio" no contrato, entao destaque e o topo por nota. */
export default function HomePage() {
  const categories = useQuery({
    queryKey: ['catalog', 'categories'],
    queryFn: () => fetchCategories(false),
  })

  const highlights = useQuery({
    queryKey: ['catalog', 'products', 'highlights'],
    queryFn: () => fetchProducts({ sort: 'rating,desc', size: 8, page: 0 }),
  })

  return (
    <>
      <PageHeader title="Loja" description="Encontre produtos por categoria ou pela busca no topo." />

      <section aria-labelledby="categorias-titulo" className="mb-10">
        <h2 id="categorias-titulo" className="mb-3 text-lg font-semibold text-ink">
          Categorias
        </h2>
        <CategoriesSection
          isLoading={categories.isLoading}
          isError={categories.isError}
          error={categories.error}
          content={categories.data?.content}
          onRetry={() => void categories.refetch()}
        />
      </section>

      <section aria-labelledby="destaques-titulo">
        <h2 id="destaques-titulo" className="mb-3 text-lg font-semibold text-ink">
          Destaques
        </h2>
        <HighlightsSection
          isLoading={highlights.isLoading}
          isError={highlights.isError}
          error={highlights.error}
          content={highlights.data?.content}
          onRetry={() => void highlights.refetch()}
        />
      </section>
    </>
  )
}

function CategoriesSection({
  isLoading,
  isError,
  error,
  content,
  onRetry,
}: {
  isLoading: boolean
  isError: boolean
  error: unknown
  content: { id: number; name: string; slug: string; productCount: number }[] | undefined
  onRetry: () => void
}) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
        {Array.from({ length: 8 }).map((_, index) => (
          <Skeleton key={index} className="h-16" />
        ))}
      </div>
    )
  }

  if (isError) {
    return (
      <ErrorState
        title="Não foi possível carregar as categorias"
        description={error instanceof ApiError ? error.message : 'Tente novamente em instantes.'}
        onRetry={onRetry}
      />
    )
  }

  if (!content || content.length === 0) {
    return <EmptyState title="Nenhuma categoria cadastrada ainda" />
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
      {content.map((category) => (
        <Link key={category.id} to={`/products?categoryId=${category.id}`}>
          <Card className="flex flex-col gap-0.5 px-4 py-3 transition-shadow hover:shadow-md">
            <span className="text-sm font-medium text-ink">{category.name}</span>
            <span className="text-xs text-muted">{category.productCount} produtos</span>
          </Card>
        </Link>
      ))}
    </div>
  )
}

function HighlightsSection({
  isLoading,
  isError,
  error,
  content,
  onRetry,
}: {
  isLoading: boolean
  isError: boolean
  error: unknown
  content: ProductSummary[] | undefined
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
    return (
      <ErrorState
        title="Não foi possível carregar os destaques"
        description={error instanceof ApiError ? error.message : 'Tente novamente em instantes.'}
        onRetry={onRetry}
      />
    )
  }

  if (!content || content.length === 0) {
    return <EmptyState title="Ainda não há produtos para destacar" />
  }

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      {content.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </div>
  )
}
