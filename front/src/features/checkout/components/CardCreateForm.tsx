'use client'

import { useEffect, useRef, useState } from 'react'
import { Button, Field, Input, Select } from '../../../components/ui'
import { ApiError, newIdempotencyKey } from '../../../lib/api'
import { onlyDigits } from '../../../lib/format'
import { isValidCpfDigits } from '../../account/validation'
import type { PaymentDetail } from '../../orders/types'
import type { Money } from '../../../types/api'
import { ErrorNote } from './ErrorNote'
import { getMercadoPago } from '../lib/mercadopago'
import type { MercadoPagoInstance } from '../lib/mercadopago'
import { useCreatePayment, usePaymentMethods } from '../queries'
import type { PaymentConfig } from '../types'

/**
 * Cartao tokenizado. Em ambiente real, o numero vai so para o SDK do Mercado Pago (nunca
 * para o nosso backend); em `fake` nao ha tokenizacao de verdade, entao o campo de token
 * fica livre para digitar qualquer coisa — o provedor falso aceita.
 */
export function CardCreateForm({
  idOrder,
  orderTotal,
  defaultEmail,
  environment,
  publicKey,
  locale,
  onCreated,
}: {
  idOrder: number
  orderTotal: Money
  defaultEmail: string
  environment: PaymentConfig['environment']
  publicKey: string
  locale: string
  onCreated: (payment: PaymentDetail) => void
}) {
  const isFake = environment === 'fake'
  const methodsQuery = usePaymentMethods(orderTotal)
  const cardMethods = (methodsQuery.data?.methods ?? []).filter((method) => method.type === 'credit_card')

  const [email, setEmail] = useState(defaultEmail)
  const [cpfDigits, setCpfDigits] = useState('')
  const [installments, setInstallments] = useState(1)
  const [idempotencyKey] = useState(() => newIdempotencyKey())
  const createPayment = useCreatePayment()

  // Modo real: dados do cartao ficam so no browser, tokenizados pelo SDK do MP.
  const [cardNumber, setCardNumber] = useState('')
  const [cardName, setCardName] = useState('')
  const [expMonth, setExpMonth] = useState('')
  const [expYear, setExpYear] = useState('')
  const [cvv, setCvv] = useState('')
  const [paymentMethodId, setPaymentMethodId] = useState<string | null>(null)
  const [issuerId, setIssuerId] = useState<string | undefined>(undefined)
  const [binStatus, setBinStatus] = useState<'idle' | 'loading' | 'done' | 'error'>('idle')
  const [tokenizing, setTokenizing] = useState(false)
  const mpRef = useRef<MercadoPagoInstance | null>(null)

  // Modo fake: sem SDK, sem tokenizacao — o front so avisa e deixa o comprador testar.
  const [fakeToken, setFakeToken] = useState('')
  const [fakePaymentMethodId, setFakePaymentMethodId] = useState('')

  useEffect(() => {
    if (isFake) return
    let cancelled = false
    getMercadoPago(publicKey, locale)
      .then((mp) => {
        if (!cancelled) mpRef.current = mp
      })
      .catch(() => {
        if (!cancelled) setBinStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [isFake, publicKey, locale])

  async function lookupBin(bin: string) {
    const mp = mpRef.current
    if (!mp || bin.length < 6) return
    setBinStatus('loading')
    try {
      const { results } = await mp.getPaymentMethods({ bin })
      const method = results[0]
      if (!method) {
        setPaymentMethodId(null)
        setBinStatus('error')
        return
      }
      setPaymentMethodId(method.id)
      setBinStatus('done')
      try {
        const issuers = await mp.getIssuers({ paymentMethodId: method.id, bin })
        setIssuerId(issuers[0]?.id)
      } catch {
        setIssuerId(undefined)
      }
    } catch {
      setPaymentMethodId(null)
      setBinStatus('error')
    }
  }

  const resolvedMethodId = isFake ? fakePaymentMethodId : paymentMethodId
  const installmentsOptions = cardMethods.find((method) => method.id === resolvedMethodId)?.installments ?? []

  const cpfValid = isValidCpfDigits(cpfDigits)
  const emailValid = /\S+@\S+\.\S+/.test(email)
  const tokenExpired = createPayment.error instanceof ApiError && createPayment.error.status === 410

  async function submitReal() {
    const mp = mpRef.current
    if (!mp || !paymentMethodId || !cpfValid || !emailValid || tokenizing) return
    setTokenizing(true)
    try {
      const token = await mp.createCardToken({
        cardNumber: onlyDigits(cardNumber),
        cardholderName: cardName,
        cardExpirationMonth: expMonth,
        cardExpirationYear: expYear,
        securityCode: cvv,
        identificationType: 'CPF',
        identificationNumber: cpfDigits,
      })
      createPayment.mutate(
        {
          body: {
            idOrder,
            method: 'credit_card',
            token: token.id,
            paymentMethodId,
            issuerId,
            installments,
            payer: { email, identification: { type: 'CPF', number: cpfDigits } },
          },
          idempotencyKey,
        },
        { onSuccess: onCreated },
      )
    } catch {
      setBinStatus('error')
    } finally {
      setTokenizing(false)
    }
  }

  function submitFake() {
    if (!fakeToken || !fakePaymentMethodId || !cpfValid || !emailValid || createPayment.isPending) return
    createPayment.mutate(
      {
        body: {
          idOrder,
          method: 'credit_card',
          token: fakeToken,
          paymentMethodId: fakePaymentMethodId,
          installments,
          payer: { email, identification: { type: 'CPF', number: cpfDigits } },
        },
        idempotencyKey,
      },
      { onSuccess: onCreated },
    )
  }

  return (
    <div className="flex flex-col gap-4">
      {isFake && (
        <ErrorNote>
          Ambiente de teste: não existe tokenização de verdade aqui. Escolha uma bandeira e digite qualquer
          valor no campo de token — o provedor falso aceita.
        </ErrorNote>
      )}

      {isFake ? (
        <Field label="Bandeira" required>
          <Select
            value={fakePaymentMethodId}
            onChange={(event) => {
              setFakePaymentMethodId(event.target.value)
              setInstallments(1)
            }}
          >
            <option value="">Selecione</option>
            {cardMethods.map((method) => (
              <option key={method.id} value={method.id}>
                {method.name}
              </option>
            ))}
          </Select>
        </Field>
      ) : (
        <Field
          label="Número do cartão"
          required
          hint={
            binStatus === 'loading'
              ? 'Identificando bandeira…'
              : binStatus === 'error'
                ? 'Não identificamos a bandeira. Confira o número.'
                : binStatus === 'done'
                  ? `Bandeira: ${cardMethods.find((method) => method.id === paymentMethodId)?.name ?? paymentMethodId}`
                  : undefined
          }
        >
          <Input
            inputMode="numeric"
            value={cardNumber}
            onChange={(event) => setCardNumber(onlyDigits(event.target.value).slice(0, 19))}
            onBlur={() => void lookupBin(onlyDigits(cardNumber).slice(0, 6))}
          />
        </Field>
      )}

      {!isFake && (
        <>
          <Field label="Nome impresso no cartão" required>
            <Input value={cardName} onChange={(event) => setCardName(event.target.value.toUpperCase())} />
          </Field>

          <div className="grid grid-cols-3 gap-3">
            <Field label="Mês" required hint="2 dígitos">
              <Input
                inputMode="numeric"
                aria-label="Mês de validade"
                value={expMonth}
                onChange={(event) => setExpMonth(onlyDigits(event.target.value).slice(0, 2))}
              />
            </Field>
            <Field label="Ano" required hint="4 dígitos">
              <Input
                inputMode="numeric"
                aria-label="Ano de validade"
                value={expYear}
                onChange={(event) => setExpYear(onlyDigits(event.target.value).slice(0, 4))}
              />
            </Field>
            <Field label="CVV" required>
              <Input
                inputMode="numeric"
                value={cvv}
                onChange={(event) => setCvv(onlyDigits(event.target.value).slice(0, 4))}
              />
            </Field>
          </div>
        </>
      )}

      <Field label="Parcelas" required hint={installmentsOptions.length === 0 ? 'Informe o cartão para ver as opções.' : undefined}>
        <Select
          value={installments}
          disabled={installmentsOptions.length === 0}
          onChange={(event) => setInstallments(Number(event.target.value))}
        >
          {installmentsOptions.length === 0 && <option value={1}>1x</option>}
          {installmentsOptions.map((option) => (
            <option key={option.quantity} value={option.quantity}>
              {option.quantity}x{option.interestFree ? ' sem juros' : ''}
            </option>
          ))}
        </Select>
      </Field>

      {isFake && (
        <Field label="Token de teste" required hint="Ambiente de teste: qualquer valor é aceito.">
          <Input value={fakeToken} onChange={(event) => setFakeToken(event.target.value)} />
        </Field>
      )}

      <Field label="E-mail" required>
        <Input type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
      </Field>

      <Field label="CPF" required hint="Somente números, 11 dígitos.">
        <Input
          inputMode="numeric"
          value={cpfDigits}
          maxLength={11}
          onChange={(event) => setCpfDigits(onlyDigits(event.target.value).slice(0, 11))}
        />
      </Field>

      {tokenExpired && <ErrorNote>O token deste cartão expirou. Preencha os dados de novo para gerar outro.</ErrorNote>}
      {!tokenExpired && createPayment.error instanceof ApiError && <ErrorNote>{createPayment.error.message}</ErrorNote>}

      {isFake ? (
        <Button
          loading={createPayment.isPending}
          disabled={!fakeToken || !fakePaymentMethodId || !cpfValid || !emailValid}
          onClick={submitFake}
        >
          Pagar (ambiente de teste)
        </Button>
      ) : (
        <Button
          loading={tokenizing || createPayment.isPending}
          disabled={!paymentMethodId || !cpfValid || !emailValid}
          onClick={() => void submitReal()}
        >
          Pagar com cartão
        </Button>
      )}
    </div>
  )
}
