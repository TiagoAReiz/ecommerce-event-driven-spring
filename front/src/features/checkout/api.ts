/** Chamadas HTTP de carrinho, checkout e pagamento, num lugar so. */
import { api } from '../../lib/api'
import type { PaymentDetail } from '../orders/types'
import type {
  Cart,
  CreateOrderRequest,
  CreateOrderResponse,
  CreatePaymentRequest,
  PaymentConfig,
  PaymentMethodsResponse,
  PaymentSyncResponse,
} from './types'

export function fetchCart(): Promise<Cart> {
  return api.get<Cart>('/cart')
}

export function addCartItem(idProduct: number, quantity: number): Promise<Cart> {
  return api.post<Cart>('/cart/items', { idProduct, quantity })
}

/** `PUT`: define a quantidade exata (idempotente), diferente do `POST` que soma. */
export function setCartItemQuantity(idProduct: number, quantity: number): Promise<Cart> {
  return api.put<Cart>(`/cart/items/${idProduct}`, { quantity })
}

export function removeCartItem(idProduct: number): Promise<Cart> {
  return api.delete<Cart>(`/cart/items/${idProduct}`)
}

/** `204`, inclusive quando ja estava vazio: a rota e idempotente. */
export function emptyCart(): Promise<void> {
  return api.delete<void>('/cart')
}

/** `POST /orders`. `Idempotency-Key` obrigatorio: clique duplo com a mesma key nao duplica. */
export function createOrder(body: CreateOrderRequest, idempotencyKey: string): Promise<CreateOrderResponse> {
  return api.post<CreateOrderResponse>('/orders', body, { idempotencyKey })
}

/** Publica, cacheavel por 1h no servidor: so a public key do MP, nunca o access token. */
export function fetchPaymentConfig(): Promise<PaymentConfig> {
  return api.get<PaymentConfig>('/payments/config', { anonymous: true })
}

export function fetchPaymentMethods(amount?: string): Promise<PaymentMethodsResponse> {
  return api.get<PaymentMethodsResponse>('/payments/methods', { query: { amount } })
}

export function fetchPayment(id: number): Promise<PaymentDetail> {
  return api.get<PaymentDetail>(`/payments/${id}`)
}

/** `POST /payments`. Idempotency-Key vira `payment.idempotency_key`, unico no servidor. */
export function createPayment(body: CreatePaymentRequest, idempotencyKey: string): Promise<PaymentDetail> {
  return api.post<PaymentDetail>('/payments', body, { idempotencyKey })
}

/**
 * Reconciliacao manual: o contrato limita a 1 chamada por minuto no mesmo pagamento e
 * devolve `429` acima disso. Quem chama e responsavel por nao apertar o gatilho toda hora.
 */
export function syncPayment(id: number): Promise<PaymentSyncResponse> {
  return api.post<PaymentSyncResponse>(`/payments/${id}/sync`)
}
