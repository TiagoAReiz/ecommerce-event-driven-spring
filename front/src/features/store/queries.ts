'use client'

/** Hooks TanStack Query da area da loja. Toda chave comeca com 'store' (exigencia da
 * tarefa) para nao colidir com o cache das areas de vitrine/conta/pedidos. */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCategories } from '@/features/catalog/api'
import {
  addPhoto,
  cancelOrder,
  cancelPayment,
  cancelShipment,
  createProduct,
  deletePhoto,
  deleteProduct,
  fetchManageOrders,
  fetchManageProduct,
  fetchManageProducts,
  fetchManageShipments,
  fetchPaymentsByOrder,
  patchProduct,
  patchShipment,
  refundPayment,
  reorderPhotos,
  syncPayment,
  updateStock,
} from './api'
import type { ManageOrdersParams, ManageProductsParams, ManageShipmentsParams } from './api'
import type {
  AddPhotoRequest,
  CancelOrderRequest,
  CancelShipmentRequest,
  CreateProductRequest,
  PatchProductRequest,
  PhotoOrderRequest,
  RefundRequest,
  ShipmentPatchRequest,
  StockUpdateRequest,
} from './types'

const keys = {
  categories: () => ['store', 'categories'] as const,
  products: {
    list: (params: ManageProductsParams) => ['store', 'products', 'list', params] as const,
    detail: (id: number) => ['store', 'products', 'detail', id] as const,
  },
  orders: {
    list: (params: ManageOrdersParams) => ['store', 'orders', 'list', params] as const,
  },
  payments: {
    byOrder: (orderId: number) => ['store', 'payments', 'byOrder', orderId] as const,
  },
  shipments: {
    list: (params: ManageShipmentsParams) => ['store', 'shipments', 'list', params] as const,
  },
}

/** `GET /categories`: rota publica, mas o formulario de produto precisa dela para o Select.
 * `includeEmpty: true` porque a loja precisa listar uma categoria mesmo com 0 produtos —
 * e' exatamente o caso do primeiro produto cadastrado nela. */
export function useCategories() {
  return useQuery({
    queryKey: keys.categories(),
    queryFn: () => fetchCategories(true),
    staleTime: 5 * 60_000,
  })
}

/* ---------- produtos ---------- */

export function useManageProducts(params: ManageProductsParams) {
  return useQuery({
    queryKey: keys.products.list(params),
    queryFn: () => fetchManageProducts(params),
    staleTime: 15_000,
  })
}

export function useManageProduct(id: number | undefined) {
  return useQuery({
    queryKey: keys.products.detail(id ?? 0),
    queryFn: () => fetchManageProduct(id as number),
    enabled: id !== undefined && Number.isFinite(id),
  })
}

function invalidateProducts(queryClient: ReturnType<typeof useQueryClient>, id?: number) {
  void queryClient.invalidateQueries({ queryKey: ['store', 'products', 'list'] })
  if (id !== undefined) void queryClient.invalidateQueries({ queryKey: keys.products.detail(id) })
}

export function useCreateProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateProductRequest) => createProduct(body),
    onSuccess: () => invalidateProducts(queryClient),
  })
}

export function usePatchProduct(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: PatchProductRequest) => patchProduct(id, body),
    onSuccess: () => invalidateProducts(queryClient, id),
  })
}

export function useUpdateStock(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: StockUpdateRequest) => updateStock(id, body),
    onSuccess: () => invalidateProducts(queryClient, id),
  })
}

export function useDeleteProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteProduct(id),
    onSuccess: (_data, id) => invalidateProducts(queryClient, id),
  })
}

export function useAddPhoto(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: AddPhotoRequest) => addPhoto(id, body),
    onSuccess: () => invalidateProducts(queryClient, id),
  })
}

export function useReorderPhotos(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: PhotoOrderRequest) => reorderPhotos(id, body),
    onSuccess: () => invalidateProducts(queryClient, id),
  })
}

export function useDeletePhoto(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (photoId: number) => deletePhoto(id, photoId),
    onSuccess: () => invalidateProducts(queryClient, id),
  })
}

/* ---------- pedidos ---------- */

export function useManageOrders(params: ManageOrdersParams) {
  return useQuery({
    queryKey: keys.orders.list(params),
    queryFn: () => fetchManageOrders(params),
    staleTime: 15_000,
  })
}

export function useCancelOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: CancelOrderRequest }) => cancelOrder(id, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['store', 'orders', 'list'] })
    },
  })
}

/* ---------- pagamentos ---------- */

export function usePaymentsByOrder(orderId: number | undefined, enabled: boolean) {
  return useQuery({
    queryKey: keys.payments.byOrder(orderId ?? 0),
    queryFn: () => fetchPaymentsByOrder(orderId as number),
    enabled: enabled && orderId !== undefined,
    staleTime: 10_000,
  })
}

export function useRefundPayment(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: RefundRequest }) => refundPayment(id, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.payments.byOrder(orderId) })
      void queryClient.invalidateQueries({ queryKey: ['store', 'orders', 'list'] })
    },
  })
}

export function useSyncPayment(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => syncPayment(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.payments.byOrder(orderId) })
    },
  })
}

export function useCancelPayment(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => cancelPayment(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.payments.byOrder(orderId) })
    },
  })
}

/* ---------- envios ---------- */

export function useManageShipments(params: ManageShipmentsParams) {
  return useQuery({
    queryKey: keys.shipments.list(params),
    queryFn: () => fetchManageShipments(params),
    staleTime: 10_000,
  })
}

export function usePatchShipment() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: ShipmentPatchRequest }) => patchShipment(id, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['store', 'shipments', 'list'] })
      void queryClient.invalidateQueries({ queryKey: ['store', 'orders', 'list'] })
    },
  })
}

export function useCancelShipment() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: CancelShipmentRequest }) => cancelShipment(id, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['store', 'shipments', 'list'] })
      void queryClient.invalidateQueries({ queryKey: ['store', 'orders', 'list'] })
    },
  })
}
