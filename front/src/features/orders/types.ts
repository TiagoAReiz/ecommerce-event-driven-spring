import type { Money } from '../../types/api'

/**
 * Tipos de pedido, envio, pagamento e avaliacao.
 * Espelham os payloads de `docs/api-contracts.md` (secoes 7, 8, 9 e 10).
 */

/** Enum `order_status` do banco do servico order (V1__create_initial_schema.sql). */
export type OrderStatus = 'pending' | 'paid' | 'processing' | 'shipped' | 'delivered' | 'cancelled' | 'refunded'

/** Enum `payment_status` do servico payment. */
export type PaymentStatus = 'pending' | 'authorized' | 'captured' | 'failed' | 'refunded' | 'cancelled'

/** Enum `shipment_status` do servico shipment. */
export type ShipmentStatus =
  | 'pending'
  | 'ready_to_ship'
  | 'in_transit'
  | 'out_for_delivery'
  | 'delivered'
  | 'returned'
  | 'cancelled'

/** Estados finais do pedido: paradas do repolling (contrato + design da mudanca). */
export const FINAL_ORDER_STATUSES: OrderStatus[] = ['delivered', 'cancelled', 'refunded']

export function isFinalOrderStatus(status: OrderStatus | undefined): boolean {
  return status !== undefined && FINAL_ORDER_STATUSES.includes(status)
}

/** Linha de `GET /orders`. */
export type OrderListItem = {
  id: number
  status: OrderStatus
  itemsCost: Money
  freightCost: Money
  totalCost: Money
  itemCount: number
  firstItem: { productName: string; productPhotoUrl: string | null }
  createdAt: string
}

export type OrderItem = {
  id: number
  idProduct: number
  productName: string
  productPhotoUrl: string | null
  priceAtTime: Money
  quantity: number
  lineTotal: Money
}

/** Projecao de pagamento dentro do pedido: mantida por evento, pode ser null. */
export type OrderPaymentProjection = {
  id: number
  status: PaymentStatus
  provider: string
}

/** Projecao de envio dentro do pedido: mantida por evento, pode ser null. */
export type OrderShipmentProjection = {
  id: number
  status: ShipmentStatus
  trackingCode: string | null
}

/** `GET /orders/{id}`. */
export type OrderDetail = {
  id: number
  status: OrderStatus
  idAddress: number
  items: OrderItem[]
  itemsCost: Money
  freightCost: Money
  totalCost: Money
  payment: OrderPaymentProjection | null
  shipment: OrderShipmentProjection | null
  createdAt: string
  updatedAt: string
}

/** `GET /payments/{id}`. `detail` varia por modalidade (pix, checkout pro, cartao). */
export type PaymentDetail = {
  id: number
  idOrder: number
  value: Money
  status: PaymentStatus
  provider: string
  externalId: string | null
  method: string | null
  detail: {
    qrCode?: string
    qrCodeBase64?: string
    ticketUrl?: string
    initPoint?: string
    expiresAt?: string
    last4?: string
    brand?: string
    installments?: number
  } | null
  statusDetail: string | null
  createdAt: string
  updatedAt: string
}

export type ShipmentAddress = { city: string; state: string; zipcode: string }

/** `GET /shipments/{id}`: traz origin e destination completos. */
export type ShipmentDetail = {
  id: number
  idOrder: number
  status: ShipmentStatus
  freightTax: Money
  trackingCode: string | null
  destination: ShipmentAddress
  origin: ShipmentAddress | null
  updatedAt: string
}

/** Linha de `GET /reviews/pending`: produtos que o usuario pode avaliar. */
export type ReviewEligibility = {
  idProduct: number
  productName: string
  productPhotoUrl: string | null
  idOrder: number
  grantedAt: string
}

/**
 * Linha de `GET /reviews/mine`.
 * O contrato lido nao mostra o corpo desta rota: os campos abaixo seguem o
 * mesmo vocabulario usado em `GET /products/{id}/reviews` (id, rate, title,
 * description, createdAt, editedAt) e em `review_eligibility`
 * (idProduct, productName, productPhotoUrl, idOrder), necessarios para a
 * tela identificar de qual produto/pedido e cada avaliacao.
 */
export type MyReview = {
  id: number
  idProduct: number
  productName: string
  productPhotoUrl: string | null
  idOrder: number
  rate: number
  title: string | null
  description: string | null
  createdAt: string
  editedAt: string | null
}

/** Corpo de `POST /products/{id}/reviews`. */
export type CreateReviewRequest = {
  idOrder: number
  rate: number
  title?: string
  description?: string
}

/** Corpo de `PATCH /reviews/{id}`: so rate, title e description. */
export type UpdateReviewRequest = {
  rate?: number
  title?: string
  description?: string
}
