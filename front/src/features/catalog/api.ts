/** Chamadas do catalogo publico. So GET (e o POST de carrinho, que e de outra area mas
 * disparado daqui pelo botao "Adicionar ao carrinho"). Rotas publicas na borda vao com
 * `anonymous: true`: assim a tela funciona igual para visitante e para quem esta logado,
 * sem depender de um token que pode estar vencido. */
import { api } from '@/lib/api'
import type {
  Availability,
  Category,
  ProductDetail,
  ProductPhoto,
  ProductsResponse,
  ReviewsResponse,
  ShippingQuote,
} from './types'

export function fetchCategories(includeEmpty?: boolean): Promise<{ content: Category[] }> {
  return api.get('/categories', { query: { includeEmpty }, anonymous: true })
}

export type ProductListParams = {
  q?: string
  categoryId?: number
  minPrice?: string
  maxPrice?: string
  minRating?: string
  inStock?: boolean
  page?: number
  size?: number
  sort?: string
}

export function fetchProducts(params: ProductListParams): Promise<ProductsResponse> {
  return api.get('/products', { query: { ...params }, anonymous: true })
}

export function fetchProduct(id: string): Promise<ProductDetail> {
  return api.get(`/products/${id}`, { anonymous: true })
}

export function fetchAvailability(id: string): Promise<Availability> {
  return api.get(`/products/${id}/availability`, { anonymous: true })
}

export function fetchPhotos(id: string): Promise<ProductPhoto[]> {
  return api.get(`/products/${id}/photos`, { anonymous: true })
}

export type ReviewListParams = {
  rate?: number
  page?: number
  size?: number
  sort?: string
}

export function fetchReviews(id: string, params: ReviewListParams): Promise<ReviewsResponse> {
  return api.get(`/products/${id}/reviews`, { query: { ...params }, anonymous: true })
}

export function fetchShippingQuote(zipcode: string): Promise<ShippingQuote> {
  return api.get('/shipping/quote', { query: { zipcode }, anonymous: true })
}

/** Fora do catalogo (e do carrinho), mas e o unico jeito de "adicionar ao carrinho"
 * a partir do detalhe do produto. Nao cria tela de carrinho, so o POST. */
export function addToCart(idProduct: number, quantity: number): Promise<unknown> {
  return api.post('/cart/items', { idProduct, quantity })
}
