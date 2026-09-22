/** Tipos da area da loja (papel owner). Espelham docs/api-contracts.md secoes 7
 * (produto/categoria, faixas 1030-1700), 8 (pedido, faixas 1860-2042), 9 (pagamento,
 * faixas 2269-2520) e 10 (envio, faixas 2594-2830). Nao reaproveita tipo de outra area
 * por edicao: so por import. */
import type { Money, Page } from '@/types/api'
import type { ProductCategoryRef, ProductPhoto } from '@/features/catalog/types'
import type { OrderStatus, PaymentStatus, ShipmentStatus } from '@/features/orders/types'

/** `status` de `GET /products/manage` (contrato, linha ~1244). */
export type ProductManageStatus = 'active' | 'out_of_stock' | 'deleted' | 'all'

/**
 * Linha de `GET /products/manage`. O contrato nao mostra o JSON de exemplo desta rota,
 * so descreve em texto que ela "mostra stock bruto [e] itens com deleted_at" — por isso
 * o formato aqui e o mesmo de `ProductSummary` (vitrine publica) somado a esses dois
 * campos, que so o owner enxerga.
 */
export type ProductManageItem = {
  id: number
  name: string
  price: Money
  stock: number
  available: number
  rating: Money
  ratingCount: number
  photoUrl: string | null
  category: ProductCategoryRef
  deletedAt: string | null
}

/** `GET /products/{id}` para o owner: mesmo formato de `ProductDetail`, com `stock` sempre presente. */
export type ProductManageDetail = {
  id: number
  name: string
  description: string
  price: Money
  stock: number
  available: number
  rating: Money
  ratingCount: number
  category: ProductCategoryRef
  photos: ProductPhoto[]
  createdAt: string
  updatedAt: string
}

export type ProductPhotoInput = { photoUrl: string; position: number }

/** Corpo de `POST /products`. */
export type CreateProductRequest = {
  name: string
  description?: string
  idCategory: number
  price: string
  stock?: number
  photos?: ProductPhotoInput[]
}

/** Corpo de `PATCH /products/{id}`: campos opcionais, com ao menos um preenchido. */
export type PatchProductRequest = Partial<{
  name: string
  description: string
  idCategory: number
  price: string
}>

/** Corpo de `PATCH /products/{id}/stock`: exatamente uma das duas formas. */
export type StockUpdateRequest = { stock: number } | { delta: number }

export type StockUpdateResponse = { idProduct: number; stock: number; held: number; available: number }

/** Corpo de `PUT /products/{id}/photos/order`. */
export type PhotoOrderRequest = { order: { id: number; position: number }[] }

/** Corpo de `POST /products/{id}/photos` (variante JSON, sem `multipart/form-data`). */
export type AddPhotoRequest = { photoUrl: string; position: number }

/** Linha de `GET /orders/manage`: mesmo formato de `GET /orders`, com `idCustomer`. */
export type OrderManageItem = {
  id: number
  status: OrderStatus
  idCustomer: number
  itemsCost: Money
  freightCost: Money
  totalCost: Money
  itemCount: number
  firstItem: { productName: string; productPhotoUrl: string | null }
  createdAt: string
}

export type CancelOrderRequest = { reason: string }

/** Linha de `GET /payments?orderId={id}`: mesmo formato de `PaymentDetail` (orders/types.ts). */
export type PaymentAttempt = {
  id: number
  idOrder: number
  value: Money
  status: PaymentStatus
  provider: string
  externalId: string | null
  method: string | null
  statusDetail: string | null
  createdAt: string
  updatedAt: string
}

/** Corpo de `POST /payments/{id}/refund`. `amount` omitido = estorno total. */
export type RefundRequest = { amount?: string; reason: string }

export type RefundResponse = {
  id: number
  status: PaymentStatus
  refunded: { amount: string; externalRefundId: string; at: string }
}

export type SyncPaymentResponse = {
  id: number
  status: PaymentStatus
  changed: boolean
}

/** Linha de `GET /shipments/manage`: mesmo formato de `GET /shipments`. */
export type ShipmentManageItem = {
  id: number
  idOrder: number
  status: ShipmentStatus
  freightTax: Money
  trackingCode: string | null
  destination: { city: string; state: string; zipcode: string }
  updatedAt: string
}

/** Transicoes que a loja pode pedir via `PATCH /shipments/{id}` (contrato §10.2).
 * `delivered` fica de fora de proposito: essa transicao e' so' do comprador. */
export type ShipmentOwnerStatus = 'ready_to_ship' | 'in_transit' | 'out_for_delivery' | 'returned'

export type ShipmentPatchRequest = { status: ShipmentOwnerStatus; trackingCode?: string }

export type CancelShipmentRequest = { reason: string }

export type ManageProductsResponse = Page<ProductManageItem>
export type ManageOrdersResponse = Page<OrderManageItem>
export type ManageShipmentsResponse = Page<ShipmentManageItem>
