/**
 * Tipos de carrinho, checkout e pagamento.
 * Espelham os payloads de `docs/api-contracts.md` (secoes 8 e 9). O pagamento em si
 * (`PaymentDetail`, `PaymentStatus`) ja existe em `features/orders/types`: reusar de la
 * evita ter dois formatos para a mesma coisa vinda do mesmo endpoint (`GET /payments/{id}`).
 */
import type { Money } from '../../types/api'
import type { PaymentDetail } from '../orders/types'

/** Motivo pelo qual uma linha do carrinho nao pode ir para o checkout sem ajuste. */
export type CartIssueCode = 'PRODUCT_UNAVAILABLE' | 'INSUFFICIENT_STOCK' | 'PRICE_CHANGED' | 'HYDRATION_TIMEOUT'

/** Snapshot do produto hidratado a partir do `inventory`; ausente so em `HYDRATION_TIMEOUT`. */
export type CartItemProduct = {
  name: string
  price: Money
  photoUrl: string | null
  available: number
  active: boolean
}

export type CartItem = {
  id: number
  idProduct: number
  quantity: number
  product: CartItemProduct | null
  lineTotal: Money
  issues: CartIssueCode[]
}

/** `GET /cart`. Carrinho vazio tambem e `200`, com `items: []`. */
export type Cart = {
  id: number
  items: CartItem[]
  itemsCost: Money
  issues: CartIssueCode[]
  updatedAt: string
}

/** Issues que travam o avanco para o checkout: o servidor nao se corrige sozinho. */
export const BLOCKING_CART_ISSUES: CartIssueCode[] = ['PRODUCT_UNAVAILABLE', 'INSUFFICIENT_STOCK', 'HYDRATION_TIMEOUT']

/** Corpo de `POST /orders`. Frete nunca sai daqui: e sempre recalculado no servidor. */
export type CreateOrderRequest = {
  addressId: number
  expectedTotalCost?: Money
}

/** Resposta `201` de `POST /orders`. `stockReservation` ainda nao e garantia de estoque. */
export type CreateOrderResponse = {
  id: number
  status: string
  stockReservation: string
  idAddress: number
  itemsCost: Money
  freightCost: Money
  totalCost: Money
  nextStep: { action: string; href: string }
  createdAt: string
}

/** `GET /payments/config`. Publica: so a public key, nunca o access token do MP. */
export type PaymentConfig = {
  provider: string
  publicKey: string
  locale: string
  currency: string
  environment: 'sandbox' | 'production' | 'fake'
  enabledMethods: PaymentMethodId[]
}

export type PaymentMethodId = 'pix' | 'credit_card' | 'checkout_pro'

export type PaymentMethodInstallment = {
  quantity: number
  amount: Money
  totalAmount: Money
  interestFree: boolean
}

/** Linha de `GET /payments/methods`: PIX nao tem `installments`, cartao tem. */
export type PaymentMethodOption = {
  id: string
  name: string
  type: string
  thumbnail?: string
  minAmount?: Money
  maxAmount?: Money
  installments?: PaymentMethodInstallment[]
}

export type PaymentMethodsResponse = { methods: PaymentMethodOption[] }

export type PayerIdentification = { type: 'CPF'; number: string }
export type Payer = { email: string; identification?: PayerIdentification }

/** Corpo de `POST /payments`: a forma exata depende do `method`. */
export type CreatePaymentRequest =
  | { idOrder: number; method: 'checkout_pro' }
  | { idOrder: number; method: 'pix'; payer: Payer }
  | {
      idOrder: number
      method: 'credit_card'
      token: string
      paymentMethodId: string
      issuerId?: string
      installments: number
      payer: Payer
    }

/** `POST /payments/{id}/sync`: o pagamento de volta, mais se algo mudou. */
export type PaymentSyncResponse = PaymentDetail & { changed: boolean }

/** Traducao para o comprador dos `statusDetail` de recusa mais comuns do MP. */
export const CARD_REJECTION_MESSAGES: Record<string, string> = {
  cc_rejected_call_for_authorize: 'O banco pediu para você ligar e autorizar esta compra antes de tentar de novo.',
  cc_rejected_insufficient_amount: 'O cartão não tem limite suficiente para este valor.',
  cc_rejected_bad_filled_security_code: 'O código de segurança (CVV) está incorreto.',
  cc_rejected_bad_filled_date: 'A validade do cartão está incorreta.',
  cc_rejected_bad_filled_other: 'Algum dado do cartão está incorreto. Confira e tente de novo.',
  cc_rejected_blacklist: 'O cartão não pôde ser autorizado pelo banco emissor.',
  cc_rejected_card_disabled: 'O cartão está desativado. Ligue para o banco emissor.',
  cc_rejected_duplicated_payment: 'Já existe um pagamento igual a este recém-feito.',
  cc_rejected_high_risk: 'O pagamento foi recusado por segurança.',
  cc_rejected_max_attempts: 'Número máximo de tentativas atingido. Use outro cartão.',
  cc_rejected_other_reason: 'O banco emissor recusou o pagamento.',
}

export function translateRejection(statusDetail: string | null): string {
  if (!statusDetail) return 'O pagamento foi recusado pelo banco emissor.'
  return CARD_REJECTION_MESSAGES[statusDetail] ?? 'O pagamento foi recusado pelo banco emissor.'
}
