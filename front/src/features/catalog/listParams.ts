import type { ProductListParams } from './api'

export const PAGE_SIZE = 20

/** Le os filtros direto da URL: ela e a fonte da verdade, assim o link e compartilhavel
 * e o back/forward do navegador funciona sem estado escondido em componente.
 *
 * <p>Fica fora da tela porque a rota servidor usa a mesma leitura para buscar o
 * initialData, e o que esta num arquivo 'use client' nao pode ser chamado do servidor.
 */
export function readParams(searchParams: URLSearchParams): ProductListParams {
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
