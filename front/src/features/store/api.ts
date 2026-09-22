/** Chamadas HTTP da area da loja (papel owner). Todas exigem Bearer com `owner` —
 * a rota ja chega protegida por `RequireAuth ownerOnly` no router, entao nenhuma
 * chamada aqui usa `anonymous: true`. */
import { api, newIdempotencyKey } from '@/lib/api'
import type { ProductPhoto } from '@/features/catalog/types'
import type {
  AddPhotoRequest,
  CancelOrderRequest,
  CancelShipmentRequest,
  CreateProductRequest,
  ManageOrdersResponse,
  ManageProductsResponse,
  ManageShipmentsResponse,
  PatchProductRequest,
  PaymentAttempt,
  PhotoOrderRequest,
  PhotoUploadUrlRequest,
  PhotoUploadUrlResponse,
  ProductManageDetail,
  ProductManageStatus,
  RefundRequest,
  RefundResponse,
  ShipmentPatchRequest,
  StockUpdateRequest,
  StockUpdateResponse,
  SyncPaymentResponse,
} from './types'
import type { OrderDetail, OrderStatus, ShipmentDetail, ShipmentStatus } from '@/features/orders/types'

/* ---------- produtos (contrato §7, faixas 1030-1700) ---------- */

export type ManageProductsParams = {
  status?: ProductManageStatus
  q?: string
  page?: number
  size?: number
}

export function fetchManageProducts(params: ManageProductsParams): Promise<ManageProductsResponse> {
  return api.get('/products/manage', { query: { ...params } })
}

/** `GET /products/{id}` sem `anonymous`: so assim `stock` bruto vem preenchido para o owner. */
export function fetchManageProduct(id: number): Promise<ProductManageDetail> {
  return api.get(`/products/${id}`)
}

export function createProduct(body: CreateProductRequest): Promise<ProductManageDetail> {
  return api.post('/products', body, { idempotencyKey: newIdempotencyKey() })
}

export function patchProduct(id: number, body: PatchProductRequest): Promise<ProductManageDetail> {
  return api.patch(`/products/${id}`, body)
}

export function updateStock(id: number, body: StockUpdateRequest): Promise<StockUpdateResponse> {
  return api.patch(`/products/${id}/stock`, body)
}

export function deleteProduct(id: number): Promise<void> {
  return api.delete(`/products/${id}`)
}

export function addPhoto(id: number, body: AddPhotoRequest): Promise<ProductPhoto> {
  return api.post(`/products/${id}/photos`, body, { idempotencyKey: newIdempotencyKey() })
}

/** Pede a URL assinada de envio (PUT) ao S3 para produto ja existente (tela de edicao).
 * Para a tela de criacao, sem id ainda, usar `requestDraftPhotoUploadUrl`. */
export function requestPhotoUploadUrl(id: number, body: PhotoUploadUrlRequest): Promise<PhotoUploadUrlResponse> {
  return api.post(`/products/${id}/photos/upload-url`, body)
}

/** Mesma ideia, mas sem id de produto (tela de criacao): o objeto cai numa area de
 * rascunho e o backend promove para a pasta do produto quando a `publicUrl` daqui
 * entra em `photos[]` do `POST /products`. */
export function requestDraftPhotoUploadUrl(body: PhotoUploadUrlRequest): Promise<PhotoUploadUrlResponse> {
  return api.post('/products/photos/upload-url', body)
}

export function reorderPhotos(id: number, body: PhotoOrderRequest): Promise<ProductPhoto[]> {
  return api.put(`/products/${id}/photos/order`, body)
}

export function deletePhoto(id: number, photoId: number): Promise<void> {
  return api.delete(`/products/${id}/photos/${photoId}`)
}

/* ---------- pedidos (contrato §8, faixas 1860-2042) ---------- */

export type ManageOrdersParams = {
  /** O helper de `query` so aceita valor escalar por chave (sem repetir `status=`
   * na URL como o contrato permite), entao a loja filtra um status por vez. */
  status?: OrderStatus
  customerId?: number
  from?: string
  to?: string
  page?: number
  size?: number
}

export function fetchManageOrders(params: ManageOrdersParams): Promise<ManageOrdersResponse> {
  return api.get('/orders/manage', { query: { ...params } })
}

export function cancelOrder(id: number, body: CancelOrderRequest): Promise<OrderDetail> {
  return api.post(`/orders/${id}/cancel`, body, { idempotencyKey: newIdempotencyKey() })
}

/* ---------- pagamentos (contrato §9, faixas 2269-2520) ---------- */

export function fetchPaymentsByOrder(orderId: number): Promise<PaymentAttempt[]> {
  return api.get('/payments', { query: { orderId } })
}

export function refundPayment(id: number, body: RefundRequest): Promise<RefundResponse> {
  // Idempotency-Key e' obrigatorio nesta rota (contrato): sem ele o gateway recusa com 400.
  return api.post(`/payments/${id}/refund`, body, { idempotencyKey: newIdempotencyKey() })
}

export function syncPayment(id: number): Promise<SyncPaymentResponse> {
  return api.post(`/payments/${id}/sync`)
}

export function cancelPayment(id: number): Promise<PaymentAttempt> {
  return api.post(`/payments/${id}/cancel`)
}

/* ---------- envios (contrato §10, faixas 2594-2830) ---------- */

export type ManageShipmentsParams = {
  status?: ShipmentStatus
  orderId?: number
  page?: number
  size?: number
}

export function fetchManageShipments(params: ManageShipmentsParams): Promise<ManageShipmentsResponse> {
  return api.get('/shipments/manage', { query: { ...params } })
}

export function patchShipment(id: number, body: ShipmentPatchRequest): Promise<ShipmentDetail> {
  return api.patch(`/shipments/${id}`, body)
}

export function cancelShipment(id: number, body: CancelShipmentRequest): Promise<ShipmentDetail> {
  return api.post(`/shipments/${id}/cancel`, body, { idempotencyKey: newIdempotencyKey() })
}
