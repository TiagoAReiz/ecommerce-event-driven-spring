import { Badge } from '@/components/ui'
import type { OrderStatus, PaymentStatus, ShipmentStatus } from '@/features/orders/types'
import type { ProductManageItem } from '../types'

type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'

const ORDER_LABEL: Record<OrderStatus, string> = {
  pending: 'Aguardando pagamento',
  paid: 'Pago',
  processing: 'Em preparação',
  shipped: 'Enviado',
  delivered: 'Entregue',
  cancelled: 'Cancelado',
  refunded: 'Estornado',
}

const ORDER_TONE: Record<OrderStatus, Tone> = {
  pending: 'warning',
  paid: 'info',
  processing: 'info',
  shipped: 'info',
  delivered: 'success',
  cancelled: 'danger',
  refunded: 'danger',
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge tone={ORDER_TONE[status]}>{ORDER_LABEL[status]}</Badge>
}

const PAYMENT_LABEL: Record<PaymentStatus, string> = {
  pending: 'Aguardando pagamento',
  authorized: 'Autorizado',
  captured: 'Capturado',
  failed: 'Falhou',
  refunded: 'Estornado',
  cancelled: 'Cancelado',
}

const PAYMENT_TONE: Record<PaymentStatus, Tone> = {
  pending: 'warning',
  authorized: 'info',
  captured: 'success',
  failed: 'danger',
  refunded: 'danger',
  cancelled: 'danger',
}

export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <Badge tone={PAYMENT_TONE[status]}>{PAYMENT_LABEL[status]}</Badge>
}

const SHIPMENT_LABEL: Record<ShipmentStatus, string> = {
  pending: 'Aguardando despacho',
  ready_to_ship: 'Pronto para envio',
  in_transit: 'Em trânsito',
  out_for_delivery: 'Saiu para entrega',
  delivered: 'Entregue',
  returned: 'Devolvido',
  cancelled: 'Cancelado',
}

const SHIPMENT_TONE: Record<ShipmentStatus, Tone> = {
  pending: 'neutral',
  ready_to_ship: 'warning',
  in_transit: 'info',
  out_for_delivery: 'info',
  delivered: 'success',
  returned: 'danger',
  cancelled: 'danger',
}

export function ShipmentStatusBadge({ status }: { status: ShipmentStatus }) {
  return <Badge tone={SHIPMENT_TONE[status]}>{SHIPMENT_LABEL[status]}</Badge>
}

/** Status "visual" do produto na gestao: a API nao devolve um enum pronto pra isso,
 * so' `deletedAt` e `stock`/`available` — a tela deriva o rotulo a partir deles. */
export function ProductStatusBadge({ product }: { product: Pick<ProductManageItem, 'deletedAt' | 'available'> }) {
  if (product.deletedAt) return <Badge tone="danger">Removido</Badge>
  if (product.available <= 0) return <Badge tone="warning">Sem estoque</Badge>
  return <Badge tone="success">Ativo</Badge>
}
