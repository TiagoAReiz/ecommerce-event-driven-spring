import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addCartItem,
  createOrder,
  createPayment,
  emptyCart,
  fetchCart,
  fetchPayment,
  fetchPaymentConfig,
  fetchPaymentMethods,
  removeCartItem,
  setCartItemQuantity,
  syncPayment,
} from './api'
import type { CreateOrderRequest, CreatePaymentRequest } from './types'

/** Carrinho usa a key `['cart']`: outras areas (ex.: catalogo) invalidam essa mesma key. */
export const cartKeys = {
  root: ['cart'] as const,
}

/** O restante desta area comeca em `checkout`, como a tarefa pede. */
export const checkoutKeys = {
  paymentConfig: ['checkout', 'payment-config'] as const,
  paymentMethods: (amount: string | undefined) => ['checkout', 'payment-methods', amount ?? null] as const,
  payment: (id: number) => ['checkout', 'payment', id] as const,
}

/** `GET /cart`. Sem `staleTime`: preco e estoque mudam por fora, o carrinho tem que refletir. */
export function useCart() {
  return useQuery({
    queryKey: cartKeys.root,
    queryFn: fetchCart,
    staleTime: 0,
  })
}

export function useAddCartItem() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ idProduct, quantity }: { idProduct: number; quantity: number }) => addCartItem(idProduct, quantity),
    onSuccess: (cart) => queryClient.setQueryData(cartKeys.root, cart),
  })
}

export function useSetCartItemQuantity() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ idProduct, quantity }: { idProduct: number; quantity: number }) =>
      setCartItemQuantity(idProduct, quantity),
    onSuccess: (cart) => queryClient.setQueryData(cartKeys.root, cart),
  })
}

export function useRemoveCartItem() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (idProduct: number) => removeCartItem(idProduct),
    onSuccess: (cart) => queryClient.setQueryData(cartKeys.root, cart),
  })
}

export function useEmptyCart() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => emptyCart(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cartKeys.root }),
  })
}

/** `POST /orders`: fecha o carrinho. Ao sair de `pending` o carrinho no servidor esvazia. */
export function useCreateOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: CreateOrderRequest; idempotencyKey: string }) =>
      createOrder(body, idempotencyKey),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: cartKeys.root }),
  })
}

/** `GET /payments/config`: publica, muda raramente — cache de 1h como o proprio servidor manda. */
export function usePaymentConfig() {
  return useQuery({
    queryKey: checkoutKeys.paymentConfig,
    queryFn: fetchPaymentConfig,
    staleTime: 60 * 60 * 1000,
  })
}

/** `GET /payments/methods?amount=`: so busca quando o valor final e conhecido. */
export function usePaymentMethods(amount: string | undefined) {
  return useQuery({
    queryKey: checkoutKeys.paymentMethods(amount),
    queryFn: () => fetchPaymentMethods(amount),
    enabled: Boolean(amount),
    staleTime: 5 * 60 * 1000,
  })
}

export function useCreatePayment() {
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: CreatePaymentRequest; idempotencyKey: string }) =>
      createPayment(body, idempotencyKey),
  })
}

/** Status em que o pagamento parou de mudar: acompanhar depois disso e so gastar rede. */
const FINAL_PAYMENT_STATUSES = new Set(['captured', 'failed', 'refunded', 'cancelled'])

/**
 * `GET /payments/{id}` relido a cada 5s (contrato do PIX), ate status final ou `expiresAt`
 * passar. Fora isso o TanStack ja para sozinho com a aba oculta (`refetchIntervalInBackground`
 * no padrao `false`), entao nao repetimos esse controle aqui.
 */
export function usePaymentPolling(paymentId: number | undefined, enabled: boolean) {
  return useQuery({
    queryKey: checkoutKeys.payment(paymentId ?? 0),
    queryFn: () => fetchPayment(paymentId as number),
    enabled: Boolean(paymentId) && enabled,
    staleTime: 0,
    refetchInterval: (query) => {
      const data = query.state.data
      if (!data) return 5000
      if (FINAL_PAYMENT_STATUSES.has(data.status)) return false
      const expiresAt = data.detail?.expiresAt
      if (expiresAt && Date.now() > new Date(expiresAt).getTime()) return false
      return 5000
    },
  })
}

/** `POST /payments/{id}/sync`: so reconciliacao manual, quem chama controla o cooldown de 1/min. */
export function useSyncPayment(paymentId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => syncPayment(paymentId),
    onSuccess: (data) => queryClient.setQueryData(checkoutKeys.payment(paymentId), data),
  })
}
