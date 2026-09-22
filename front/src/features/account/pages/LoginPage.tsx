'use client'

import { useEffect } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Button, Card } from '../../../components/ui'
import { useAuth } from '../../../lib/auth'

/**
 * `/login`: unica porta de entrada, sempre pelo Google (decisao registrada no
 * CLAUDE.md — sem senha armazenada). `RequireAuth` manda para ca com a query
 * `from`, entao devolvemos o usuario para o mesmo lugar apos o login.
 */
export default function LoginPage() {
  const { status, login } = useAuth()
  const router = useRouter()
  const searchParams = useSearchParams()
  const from = searchParams.get('from') ?? '/account'

  useEffect(() => {
    // Ja logado (ex.: voltou para /login por engano): nao mostra o botao de novo.
    if (status === 'authenticated') router.replace(from)
  }, [status, from, router])

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <Card className="w-full max-w-sm p-8 text-center">
        <h1 className="text-xl font-semibold text-ink">Entrar na loja</h1>
        <p className="mt-2 text-sm text-muted">
          O login e feito pela sua conta Google. Nao guardamos senha nenhuma.
        </p>
        <Button className="mt-6 w-full" onClick={() => login(from)} disabled={status === 'loading'}>
          Entrar com Google
        </Button>
      </Card>
    </div>
  )
}
