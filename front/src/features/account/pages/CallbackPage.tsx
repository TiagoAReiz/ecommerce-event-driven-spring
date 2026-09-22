'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Card, LinkButton, Spinner } from '../../../components/ui'
import { consumeTokenFromFragment, useAuth } from '../../../lib/auth'

/**
 * `/callback`: e para onde o gateway devolve o browser depois do Google
 * (`/callback#token=...`). Rota fixa, nao mexer no caminho.
 */
export default function CallbackPage() {
  const { reload } = useAuth()
  const router = useRouter()
  const [failed, setFailed] = useState(false)
  // StrictMode roda o effect duas vezes em dev; o fragmento so existe na primeira.
  const consumed = useRef(false)

  useEffect(() => {
    if (consumed.current) return
    consumed.current = true

    const { ok, redirectTo } = consumeTokenFromFragment()
    if (!ok) {
      setFailed(true)
      return
    }

    void reload().then(() => router.replace(redirectTo))
  }, [reload, router])

  if (failed) {
    return (
      <div className="flex min-h-[70vh] items-center justify-center">
        <Card className="w-full max-w-sm p-8 text-center">
          <h1 className="text-xl font-semibold text-ink">Não deu para entrar</h1>
          <p className="mt-2 text-sm text-muted">
            O Google não devolveu um token válido. Tente entrar de novo.
          </p>
          <LinkButton to="/login" className="mt-6 w-full">
            Voltar para o login
          </LinkButton>
        </Card>
      </div>
    )
  }

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <Spinner className="h-8 w-8 text-brand-700" />
    </div>
  )
}
