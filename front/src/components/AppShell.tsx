'use client'

import { Suspense, useState } from 'react'
import type { ReactNode } from 'react'
import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { Button, Input } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { cx } from '@/lib/format'

/** Casca de todas as telas: cabecalho, conteudo e rodape. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-white">
      <Header />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-16">{children}</main>
      <Footer />
    </div>
  )
}

function Header() {
  const { user, status, isOwner, login, logout } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-white/95 backdrop-blur">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 px-4 py-3">
        <Link href="/" className="text-lg font-semibold tracking-tight text-brand-700">
          Loja
        </Link>

        <nav className="order-3 flex w-full gap-4 text-sm sm:order-none sm:w-auto">
          <Link
            href="/products"
            className={cx('py-1 text-muted hover:text-brand-700', pathname.startsWith('/products') && 'font-medium text-brand-700')}
          >
            Produtos
          </Link>
          {isOwner && (
            <Link
              href="/store"
              className={cx('py-1 text-muted hover:text-brand-700', pathname.startsWith('/store') && 'font-medium text-brand-700')}
            >
              Área da loja
            </Link>
          )}
        </nav>

        {/* Em Suspense porque le a query string: sem a fronteira, o Next nao
            consegue pre-renderizar as paginas que passam por esta casca. */}
        <Suspense fallback={<div className="order-4 h-10 w-full flex-1 sm:order-none sm:w-auto sm:max-w-sm" />}>
          <SearchBox />
        </Suspense>

        <div className="ml-auto flex items-center gap-2">
          <Link href="/cart"
            className="rounded-[8px] px-3 py-2 text-sm text-muted hover:bg-brand-50 hover:text-brand-700"
          >
            Carrinho
          </Link>

          {status === 'authenticated' && user ? (
            <div className="relative">
              <button
                type="button"
                onClick={() => setMenuOpen((open) => !open)}
                className="flex items-center gap-2 rounded-[8px] px-2 py-1.5 text-sm text-ink hover:bg-brand-50"
                aria-expanded={menuOpen}
                aria-haspopup="menu"
              >
                {user.photoUrl ? (
                  <img src={user.photoUrl} alt="" className="h-7 w-7 rounded-full object-cover" />
                ) : (
                  <span className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100 text-xs font-medium text-brand-700">
                    {user.name.slice(0, 1).toUpperCase()}
                  </span>
                )}
                <span className="hidden max-w-[10rem] truncate sm:block">{user.name}</span>
              </button>

              {menuOpen && (
                <div
                  role="menu"
                  className="absolute right-0 mt-2 w-56 rounded-[12px] border border-line bg-white py-1 shadow-lg"
                  onMouseLeave={() => setMenuOpen(false)}
                >
                  <MenuLink to="/account" onClick={() => setMenuOpen(false)}>
                    Minha conta
                  </MenuLink>
                  <MenuLink to="/orders" onClick={() => setMenuOpen(false)}>
                    Meus pedidos
                  </MenuLink>
                  <MenuLink to="/reviews/pending" onClick={() => setMenuOpen(false)}>
                    Avaliar compras
                  </MenuLink>
                  {isOwner && (
                    <MenuLink to="/store" onClick={() => setMenuOpen(false)}>
                      Área da loja
                    </MenuLink>
                  )}
                  <button
                    type="button"
                    role="menuitem"
                    onClick={() => {
                      setMenuOpen(false)
                      void logout().then(() => router.push('/'))
                    }}
                    className="block w-full px-4 py-2 text-left text-sm text-muted hover:bg-brand-50 hover:text-brand-700"
                  >
                    Sair
                  </button>
                </div>
              )}
            </div>
          ) : (
            <Button size="sm" onClick={() => login(pathname)} disabled={status === 'loading'}>
              Entrar
            </Button>
          )}
        </div>
      </div>
    </header>
  )
}

/** Busca do cabecalho. Isolada por causa da leitura da query string. */
function SearchBox() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [term, setTerm] = useState(searchParams.get('q') ?? '')

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    router.push(term.trim() ? `/products?q=${encodeURIComponent(term.trim())}` : '/products')
  }

  return (
    <form onSubmit={submitSearch} className="order-4 w-full flex-1 sm:order-none sm:w-auto sm:max-w-sm">
      <Input
        type="search"
        value={term}
        onChange={(event) => setTerm(event.target.value)}
        aria-label="Buscar produtos"
        className="h-10"
      />
    </form>
  )
}

function MenuLink({ to, children, onClick }: { to: string; children: ReactNode; onClick: () => void }) {
  return (
    <Link href={to}
      role="menuitem"
      onClick={onClick}
      className="block px-4 py-2 text-sm text-muted hover:bg-brand-50 hover:text-brand-700"
    >
      {children}
    </Link>
  )
}

function Footer() {
  return (
    <footer className="border-t border-line bg-brand-50/40">
      <div className="mx-auto flex max-w-6xl flex-col gap-1 px-4 py-8 text-sm text-muted">
        <p className="font-medium text-ink">Loja</p>
        <p>Pagamento por PIX, cartão e Checkout Pro. Entrega para todo o Brasil.</p>
      </div>
    </footer>
  )
}
