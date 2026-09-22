import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import { Suspense, type ReactNode } from 'react'
import { AppShell } from '@/components/AppShell'
import { Spinner } from '@/components/ui'
import { Providers } from './providers'
import './globals.css'

// A fonte vem pelo next/font: ele baixa no build e serve do proprio dominio, sem
// ida ao Google no carregamento da pagina.
const inter = Inter({ subsets: ['latin'], display: 'swap', variable: '--font-inter' })

export const metadata: Metadata = {
  title: { default: 'Loja', template: '%s · Loja' },
  description: 'Vitrine, carrinho e acompanhamento de pedidos.',
}

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR" className={inter.variable}>
      <body>
        <Providers>
          <AppShell>
            {/* As telas com sessao leem a query string e so existem no navegador.
                A fronteira fica aqui para que cada uma nao precise repeti-la, e
                para a vitrine continuar saindo pronta do servidor. */}
            <Suspense
              fallback={
                <div className="flex justify-center py-24">
                  <Spinner className="h-8 w-8 text-brand-700" />
                </div>
              }
            >
              {children}
            </Suspense>
          </AppShell>
        </Providers>
      </body>
    </html>
  )
}
