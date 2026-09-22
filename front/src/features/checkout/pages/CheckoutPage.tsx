'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { EmptyState, ErrorState, LinkButton, PageHeader, Skeleton } from '../../../components/ui'
import { ApiError } from '../../../lib/api'
import { AddressStep } from '../components/AddressStep'
import { ReviewStep } from '../components/ReviewStep'
import { useCart } from '../queries'
import { BLOCKING_CART_ISSUES } from '../types'
import type { Address } from '../../account/types'

type Step = 'address' | 'review'

/** `/checkout`: endereco -> frete e revisao -> `POST /orders`. */
export default function CheckoutPage() {
  const router = useRouter()
  const cartQuery = useCart()
  const [step, setStep] = useState<Step>('address')
  const [selectedAddress, setSelectedAddress] = useState<Address | null>(null)

  if (cartQuery.isLoading) {
    return (
      <>
        <PageHeader title="Checkout" />
        <Skeleton className="h-64 w-full" />
      </>
    )
  }

  if (cartQuery.isError) {
    return (
      <>
        <PageHeader title="Checkout" />
        <ErrorState
          title="Não deu para carregar seu carrinho"
          description={cartQuery.error instanceof ApiError ? cartQuery.error.message : undefined}
          onRetry={() => void cartQuery.refetch()}
        />
      </>
    )
  }

  const cart = cartQuery.data
  if (!cart) return null

  if (cart.items.length === 0) {
    return (
      <>
        <PageHeader title="Checkout" />
        <EmptyState
          title="Seu carrinho está vazio"
          description="Volte para a vitrine e adicione produtos antes de fechar o pedido."
          action={<LinkButton to="/products">Ver produtos</LinkButton>}
        />
      </>
    )
  }

  const hasBlockingIssue = cart.items.some((item) => item.issues.some((issue) => BLOCKING_CART_ISSUES.includes(issue)))
  if (hasBlockingIssue) {
    return (
      <>
        <PageHeader title="Checkout" />
        <EmptyState
          title="Seu carrinho precisa de ajustes"
          description="Algum item ficou indisponível ou sem estoque suficiente. Ajuste o carrinho para continuar."
          action={<LinkButton to="/cart">Voltar ao carrinho</LinkButton>}
        />
      </>
    )
  }

  return (
    <>
      <PageHeader
        title="Checkout"
        description={step === 'address' ? 'Escolha o endereço de entrega.' : 'Confira o pedido antes de confirmar.'}
      />

      {step === 'address' && (
        <AddressStep
          selectedId={selectedAddress?.id ?? null}
          onSelect={setSelectedAddress}
          onContinue={() => setStep('review')}
        />
      )}

      {step === 'review' && selectedAddress && (
        <ReviewStep
          cart={cart}
          address={selectedAddress}
          onBack={() => setStep('address')}
          onConfirmed={(orderId) => router.push(`/checkout/payment/${orderId}`)}
        />
      )}
    </>
  )
}
