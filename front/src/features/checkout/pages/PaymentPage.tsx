import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../../components/ui'
import { ApiError } from '../../../lib/api'
import { money } from '../../../lib/format'
import { useAuth } from '../../../lib/auth'
import { useOrder } from '../../orders/queries'
import type { PaymentDetail } from '../../orders/types'
import { CardCreateForm } from '../components/CardCreateForm'
import { CheckoutProCreateForm } from '../components/CheckoutProCreateForm'
import { PixCreateForm } from '../components/PixCreateForm'
import { PaymentTracker } from '../components/PaymentTracker'
import { usePaymentConfig, usePaymentPolling } from '../queries'
import type { PaymentMethodId } from '../types'

/** Status do pagamento em que ainda faz sentido continuar acompanhando este id. */
const TRACKABLE_PAYMENT_STATUSES = new Set(['pending', 'authorized', 'captured'])

const METHOD_LABEL: Record<PaymentMethodId, string> = {
  pix: 'PIX',
  credit_card: 'Cartão',
  checkout_pro: 'Checkout Pro',
}

/** `/checkout/payment/:orderId`: escolhe a modalidade, cria em `POST /payments`, acompanha. */
export default function PaymentPage() {
  const params = useParams<{ orderId: string }>()
  const orderId = Number(params.orderId)
  const validOrderId = Number.isFinite(orderId) && orderId > 0
  const navigate = useNavigate()
  const { user } = useAuth()

  const orderQuery = useOrder(validOrderId ? orderId : Number.NaN)
  const configQuery = usePaymentConfig()

  const [method, setMethod] = useState<PaymentMethodId | null>(null)
  const [createdPaymentId, setCreatedPaymentId] = useState<number | null>(null)

  const existingPayment = orderQuery.data?.payment
  const existingPaymentId =
    existingPayment && TRACKABLE_PAYMENT_STATUSES.has(existingPayment.status) ? existingPayment.id : undefined
  const activePaymentId = createdPaymentId ?? existingPaymentId

  const paymentQuery = usePaymentPolling(activePaymentId, Boolean(activePaymentId))

  // Pagamento aprovado (nesta sessao ou numa retomada): segue para a confirmacao do pedido.
  useEffect(() => {
    if (paymentQuery.data?.status === 'captured') {
      navigate(`/checkout/done/${orderId}`, { replace: true })
    }
  }, [paymentQuery.data?.status, navigate, orderId])

  // Pedido ja saiu de `pending` por outro caminho (ex.: webhook chegou antes do polling
  // daqui pegar o pagamento): nao ha mais o que fazer nesta tela, so seguir.
  const orderStatus = orderQuery.data?.status
  useEffect(() => {
    if (orderStatus && orderStatus !== 'pending' && orderStatus !== 'cancelled') {
      navigate(`/checkout/done/${orderId}`, { replace: true })
    }
  }, [orderStatus, navigate, orderId])

  if (!validOrderId) {
    return (
      <>
        <PageHeader title="Pagamento" />
        <EmptyState title="Pedido inválido" description="O endereço não aponta para um pedido válido." />
      </>
    )
  }

  if (orderQuery.isLoading || configQuery.isLoading) {
    return (
      <>
        <PageHeader title="Pagamento" />
        <Skeleton className="h-64 w-full" />
      </>
    )
  }

  if (orderQuery.isError) {
    const notFound = orderQuery.error instanceof ApiError && orderQuery.error.status === 404
    return (
      <>
        <PageHeader title="Pagamento" />
        {notFound ? (
          <EmptyState title="Pedido não encontrado" description="Ele pode ser de outra conta ou não existir." />
        ) : (
          <ErrorState
            title="Não foi possível carregar o pedido"
            description={orderQuery.error instanceof ApiError ? orderQuery.error.message : undefined}
            onRetry={() => void orderQuery.refetch()}
          />
        )}
      </>
    )
  }

  if (configQuery.isError) {
    return (
      <>
        <PageHeader title="Pagamento" />
        <ErrorState
          title="Não foi possível carregar as formas de pagamento"
          description={configQuery.error instanceof ApiError ? configQuery.error.message : undefined}
          onRetry={() => void configQuery.refetch()}
        />
      </>
    )
  }

  const order = orderQuery.data
  const config = configQuery.data
  if (!order || !config) return null

  if (order.status === 'cancelled') {
    return (
      <>
        <PageHeader title="Pagamento" />
        <EmptyState
          title="Este pedido foi cancelado"
          description="Não é mais possível pagar por ele."
          action={
            <Button variant="secondary" onClick={() => navigate(`/orders/${order.id}`)}>
              Ver pedido
            </Button>
          }
        />
      </>
    )
  }

  if (order.status !== 'pending') {
    // Efeito acima ja esta navegando para a confirmacao; isto so cobre o instante ate ele rodar.
    return (
      <>
        <PageHeader title="Pagamento" />
        <Skeleton className="h-64 w-full" />
      </>
    )
  }

  const defaultEmail = user?.email ?? ''

  function handleCreated(payment: PaymentDetail) {
    setCreatedPaymentId(payment.id)
  }

  function retry() {
    setCreatedPaymentId(null)
    setMethod(null)
  }

  return (
    <>
      <PageHeader title="Pagamento" description={`Pedido #${order.id} · Total ${money(order.totalCost)}`} />

      {activePaymentId ? (
        paymentQuery.isLoading || !paymentQuery.data ? (
          <Skeleton className="h-64 w-full" />
        ) : (
          <PaymentTracker payment={paymentQuery.data} onRetry={retry} />
        )
      ) : (
        <div className="flex flex-col gap-4">
          <div className="flex gap-2">
            {config.enabledMethods.map((id) => (
              <Button key={id} variant={method === id ? 'primary' : 'secondary'} size="sm" onClick={() => setMethod(id)}>
                {METHOD_LABEL[id]}
              </Button>
            ))}
          </div>

          {method === 'pix' && <PixCreateForm idOrder={order.id} defaultEmail={defaultEmail} onCreated={handleCreated} />}

          {method === 'credit_card' && (
            <CardCreateForm
              idOrder={order.id}
              orderTotal={order.totalCost}
              defaultEmail={defaultEmail}
              environment={config.environment}
              publicKey={config.publicKey}
              locale={config.locale}
              onCreated={handleCreated}
            />
          )}

          {method === 'checkout_pro' && (
            <CheckoutProCreateForm idOrder={order.id} defaultEmail={defaultEmail} onCreated={handleCreated} />
          )}

          {!method && <p className="text-sm text-muted">Escolha uma forma de pagamento para continuar.</p>}
        </div>
      )}
    </>
  )
}
