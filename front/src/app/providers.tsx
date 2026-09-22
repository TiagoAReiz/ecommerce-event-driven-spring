'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '@/lib/api'
import { AuthProvider } from '@/lib/auth'

/**
 * Sessao e cache de dados do servidor.
 *
 * <p>O QueryClient nasce dentro de estado, e nao em modulo: em modulo ele seria
 * compartilhado entre requisicoes no servidor e um usuario veria o cache do outro.
 */
export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: (failureCount, error) => {
              // Repetir um 4xx so gera o mesmo erro: quem falhou por culpa do
              // cliente nao melhora com insistencia. Rede e 5xx, sim.
              if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
              return failureCount < 2
            },
          },
          mutations: { retry: 0 },
        },
      }),
  )

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  )
}
