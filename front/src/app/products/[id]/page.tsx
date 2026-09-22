import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import ProductDetailPage from '@/features/catalog/pages/ProductDetailPage'
import { fetchPhotos, fetchProduct, fetchReviews } from '@/features/catalog/api'
import { ApiError } from '@/lib/api'

type Params = Promise<{ id: string }>

// Pagina de produto e a que mais precisa aparecer em busca: nome e descricao do
// produto viram title/description reais no HTML, nao so no client-side.
export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params
  const product = await fetchProduct(id).catch(() => undefined)
  if (!product) return {}
  return {
    title: product.name,
    description: product.description,
  }
}

export default async function Page({ params }: { params: Params }) {
  const { id } = await params

  // 404 do backend e produto que nao existe de verdade: leva pro not-found do Next.
  // Qualquer outro erro (rede, 500) nao pode derrubar a pagina: a tela cuida do
  // estado de erro no cliente, como as outras rotas servidor do catalogo.
  const product = await fetchProduct(id).catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 404) notFound()
    return undefined
  })

  const [photos, reviews] = await Promise.all([
    fetchPhotos(id).catch(() => undefined),
    fetchReviews(id, { page: 0, size: 20, sort: 'createdAt,desc' }).catch(() => undefined),
  ])

  return (
    <ProductDetailPage id={id} initialProduct={product} initialPhotos={photos} initialReviews={reviews} />
  )
}
