/** Tipos do catalogo publico, espelhando docs/api-contracts.md linhas 1030-1244 (produtos e
 * categorias), 1897-1930 (carrinho) e 2614-2653 (frete). Nao reaproveita tipo de outra area. */
import type { Money, Page } from '../../types/api'

export type Category = {
  id: number
  name: string
  slug: string
  productCount: number
}

/** Categoria embutida no produto: recorte menor, sem `productCount`. */
export type ProductCategoryRef = {
  id: number
  name: string
  slug: string
}

export type ProductSummary = {
  id: number
  name: string
  price: Money
  rating: Money
  ratingCount: number
  available: number
  photoUrl: string | null
  category: ProductCategoryRef
}

export type ProductFacets = {
  categories: { id: number; name: string; count: number }[]
  priceRange: { min: Money; max: Money }
}

export type ProductsResponse = Page<ProductSummary> & { facets: ProductFacets }

export type ProductPhoto = {
  id: number
  photoUrl: string
  position: number
}

export type ProductDetail = {
  id: number
  name: string
  description: string
  price: Money
  /** So vem preenchido para o `owner`; publico ve so `available`. */
  stock?: number
  available: number
  rating: Money
  ratingCount: number
  category: ProductCategoryRef
  photos: ProductPhoto[]
  createdAt: string
  updatedAt: string
}

export type Availability = {
  idProduct: number
  stock: number
  held: number
  available: number
  asOf: string
}

export type ReviewAuthor = {
  id: number
  name: string
  photoUrl: string | null
}

export type Review = {
  id: number
  rate: number
  title: string
  description: string
  author: ReviewAuthor
  createdAt: string
  editedAt: string | null
}

export type ReviewSummary = {
  rating: Money
  ratingCount: number
  /** Chave e a nota (1 a 5) como string, valor e a contagem. */
  distribution: Record<string, number>
}

export type ReviewsResponse = Page<Review> & { summary: ReviewSummary }

export type ShippingQuote = {
  zipcode: string
  origin: { city: string; state: string }
  distanceKm: number
  ratePerKm: Money
  freightCost: Money
}

/** Chaves de ordenacao aceitas pelo `GET /products` (contrato, linha ~1097). */
export type ProductSort =
  | 'createdAt,desc'
  | 'createdAt,asc'
  | 'price,asc'
  | 'price,desc'
  | 'rating,desc'
  | 'name,asc'
