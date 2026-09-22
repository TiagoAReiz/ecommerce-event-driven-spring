'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, newIdempotencyKey } from '@/lib/api'
import type { Page } from '@/types/api'
import { isFinalOrderStatus } from './types'
import type {
  CreateReviewRequest,
  MyReview,
  OrderDetail,
  OrderListItem,
  OrderStatus,
  PaymentDetail,
  ReviewEligibility,
  ShipmentDetail,
  UpdateReviewRequest,
} from './types'

/** Todas as chaves de query desta area comecam com 'orders', por exigencia da tarefa. */
const keys = {
  list: (params: OrdersListParams) => ['orders', 'list', params] as const,
  detail: (id: number) => ['orders', 'detail', id] as const,
  payment: (paymentId: number) => ['orders', 'payment', paymentId] as const,
  shipment: (shipmentId: number) => ['orders', 'shipment', shipmentId] as const,
  reviewsPending: (page: number) => ['orders', 'reviews', 'pending', page] as const,
  reviewsMine: (page: number) => ['orders', 'reviews', 'mine', page] as const,
}

export type OrdersListParams = {
  status?: OrderStatus
  from?: string
  to?: string
  page?: number
  size?: number
}

/** `GET /orders`: pedidos do usuario do token. */
export function useOrders(params: OrdersListParams) {
  return useQuery({
    queryKey: keys.list(params),
    queryFn: () =>
      api.get<Page<OrderListItem>>('/orders', {
        query: { status: params.status, from: params.from, to: params.to, page: params.page, size: params.size },
      }),
    staleTime: 30_000,
  })
}

/**
 * `GET /orders/{id}`. Enquanto o status nao for final, revalida a cada 10s —
 * o pedido muda de estado por evento assincrono depois da resposta HTTP, entao
 * so o polling mantem a tela ao vivo. `refetchIntervalInBackground` fica no
 * padrao (false): com `document.hidden`, o TanStack para de buscar sozinho.
 */
export function useOrder(id: number) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => api.get<OrderDetail>(`/orders/${id}`),
    staleTime: 10_000,
    refetchInterval: (query) => {
      const data = query.state.data
      if (!data || isFinalOrderStatus(data.status)) return false
      return 10_000
    },
  })
}

/** `GET /payments/{id}`: detalhe completo do pagamento projetado no pedido. */
export function usePaymentDetail(paymentId: number | undefined) {
  return useQuery({
    queryKey: keys.payment(paymentId ?? 0),
    queryFn: () => api.get<PaymentDetail>(`/payments/${paymentId}`),
    enabled: paymentId !== undefined,
    staleTime: 30_000,
  })
}

/** `GET /shipments/{id}`: detalhe completo do envio, com origem e destino. */
export function useShipmentDetail(shipmentId: number | undefined) {
  return useQuery({
    queryKey: keys.shipment(shipmentId ?? 0),
    queryFn: () => api.get<ShipmentDetail>(`/shipments/${shipmentId}`),
    enabled: shipmentId !== undefined,
    staleTime: 10_000,
  })
}

/** `POST /orders/{id}/cancel`. Idempotency-Key evita duplicar em retry de clique. */
export function useCancelOrder(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (reason: string) =>
      api.post<OrderDetail>(`/orders/${id}/cancel`, { reason }, { idempotencyKey: newIdempotencyKey() }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['orders', 'detail', id] })
      void queryClient.invalidateQueries({ queryKey: ['orders', 'list'] })
    },
  })
}

/** `POST /shipments/{id}/confirm-delivery`. Contrato garante idempotencia por conta propria. */
export function useConfirmDelivery(orderId: number, shipmentId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<ShipmentDetail>(`/shipments/${shipmentId}/confirm-delivery`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['orders', 'shipment', shipmentId] })
      void queryClient.invalidateQueries({ queryKey: ['orders', 'detail', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['orders', 'list'] })
    },
  })
}

/** `GET /reviews/pending`: produtos que o usuario pode avaliar. */
export function usePendingReviews(page: number) {
  return useQuery({
    queryKey: keys.reviewsPending(page),
    queryFn: () => api.get<Page<ReviewEligibility>>('/reviews/pending', { query: { page } }),
    staleTime: 30_000,
  })
}

/** `GET /reviews/mine`. */
export function useMyReviews(page: number) {
  return useQuery({
    queryKey: keys.reviewsMine(page),
    queryFn: () => api.get<Page<MyReview>>('/reviews/mine', { query: { page } }),
    staleTime: 30_000,
  })
}

/** `POST /products/{id}/reviews`. */
export function useCreateReview(idProduct: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateReviewRequest) =>
      api.post<MyReview>(`/products/${idProduct}/reviews`, body, { idempotencyKey: newIdempotencyKey() }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['orders', 'reviews'] })
    },
  })
}

/** `PATCH /reviews/{id}`: so o autor, dentro da janela de 30 dias. */
export function useUpdateReview(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateReviewRequest) => api.patch<MyReview>(`/reviews/${id}`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['orders', 'reviews', 'mine'] })
    },
  })
}

/** `DELETE /reviews/{id}`. */
export function useDeleteReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete<void>(`/reviews/${id}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['orders', 'reviews'] })
    },
  })
}
