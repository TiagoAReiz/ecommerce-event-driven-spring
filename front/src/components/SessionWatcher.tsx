'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useEffect } from 'react'
import { UNAUTHORIZED_EVENT } from '@/lib/api'

/** Telas que já são o próprio login: mandar para o login de novo daria laço. */
const SEM_REDIRECIONAR = ['/login', '/callback']

/**
 * Sessao recusada em qualquer tela leva ao login, com o destino guardado.
 *
 * <p>Fica num lugar so: espalhar esse tratamento por tela garante que uma esqueca,
 * e ai a pessoa ve um erro seco em vez do caminho de volta.
 */
export function SessionWatcher() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  useEffect(() => {
    function aoPerderSessao() {
      if (SEM_REDIRECIONAR.some((rota) => pathname.startsWith(rota))) return
      const query = searchParams.toString()
      const destino = query ? `${pathname}?${query}` : pathname
      router.replace(`/login?from=${encodeURIComponent(destino)}`)
    }

    window.addEventListener(UNAUTHORIZED_EVENT, aoPerderSessao)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, aoPerderSessao)
  }, [router, pathname, searchParams])

  return null
}
