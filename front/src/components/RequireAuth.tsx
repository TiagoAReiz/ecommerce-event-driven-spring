'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { useAuth } from '@/lib/auth'
import { EmptyState, LinkButton, PageHeader, Spinner } from '@/components/ui'

/**
 * Rota que exige sessao.
 *
 * <p>Enquanto o token guardado nao foi conferido com o gateway o estado e
 * `loading`: mandar para o login aqui deslogaria quem apenas atualizou a pagina.
 */
export function RequireAuth({ children, ownerOnly }: { children: ReactNode; ownerOnly?: boolean }) {
  const { status, isOwner } = useAuth()
  const router = useRouter()
  const pathname = usePathname()

  useEffect(() => {
    if (status === 'anonymous') {
      // O destino vai junto para o login devolver o usuario onde ele estava.
      router.replace(`/login?from=${encodeURIComponent(pathname)}`)
    }
  }, [status, router, pathname])

  if (status === 'loading' || status === 'anonymous') {
    return (
      <div className="flex justify-center py-24">
        <Spinner className="h-8 w-8 text-brand-700" />
      </div>
    )
  }

  if (ownerOnly && !isOwner) {
    return (
      <>
        <PageHeader title="Área da loja" />
        <EmptyState
          title="Esta área é da loja"
          description="Sua conta não tem o papel de dono da loja."
          action={<LinkButton to="/">Voltar para a vitrine</LinkButton>}
        />
      </>
    )
  }

  return <>{children}</>
}
