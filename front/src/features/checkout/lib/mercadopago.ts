/**
 * Carregador do SDK do Mercado Pago (v2), sem dependencia npm: o contrato exige a tag
 * `<script src="https://sdk.mercadopago.com/js/v2">`. O numero do cartao entra so aqui,
 * no browser, e sai como `token` de uso unico — nunca chega ao nosso backend.
 */

const SDK_URL = 'https://sdk.mercadopago.com/js/v2'

export type MpCardTokenInput = {
  cardNumber: string
  cardholderName: string
  cardExpirationMonth: string
  cardExpirationYear: string
  securityCode: string
  identificationType: string
  identificationNumber: string
}

export type MpCardToken = { id: string }

export type MpPaymentMethodResult = {
  id: string
  name: string
  payment_type_id: string
}

export type MpIssuer = { id: string; name: string }

export interface MercadoPagoInstance {
  createCardToken(input: MpCardTokenInput): Promise<MpCardToken>
  getPaymentMethods(input: { bin: string }): Promise<{ results: MpPaymentMethodResult[] }>
  getIssuers(input: { paymentMethodId: string; bin: string }): Promise<MpIssuer[]>
}

type MercadoPagoConstructor = new (publicKey: string, options?: { locale?: string }) => MercadoPagoInstance

declare global {
  interface Window {
    MercadoPago?: MercadoPagoConstructor
  }
}

let loadPromise: Promise<MercadoPagoConstructor> | null = null

/** Injeta a tag uma vez so; chamadas seguintes reusam a mesma promise em voo. */
function loadSdk(): Promise<MercadoPagoConstructor> {
  if (window.MercadoPago) return Promise.resolve(window.MercadoPago)
  if (loadPromise) return loadPromise

  loadPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SDK_URL}"]`)
    if (existing) {
      existing.addEventListener('load', () => {
        if (window.MercadoPago) resolve(window.MercadoPago)
        else reject(new Error('SDK do Mercado Pago carregou sem expor window.MercadoPago'))
      })
      existing.addEventListener('error', () => reject(new Error('Não foi possível carregar o SDK do Mercado Pago')))
      return
    }

    const script = document.createElement('script')
    script.src = SDK_URL
    script.async = true
    script.onload = () => {
      if (window.MercadoPago) resolve(window.MercadoPago)
      else reject(new Error('SDK do Mercado Pago carregou sem expor window.MercadoPago'))
    }
    script.onerror = () => reject(new Error('Não foi possível carregar o SDK do Mercado Pago'))
    document.head.appendChild(script)
  })
  return loadPromise
}

export async function getMercadoPago(publicKey: string, locale: string): Promise<MercadoPagoInstance> {
  const Ctor = await loadSdk()
  return new Ctor(publicKey, { locale })
}
