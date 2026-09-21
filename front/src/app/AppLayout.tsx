import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Input } from '../components/ui'
import { useAuth } from '../lib/auth'
import { cx } from '../lib/format'

/** Casca de todas as telas: cabecalho, conteudo e rodape. */
export function AppLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-white">
      <Header />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pb-16">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}

function Header() {
  const { user, status, isOwner, login, logout } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [term, setTerm] = useState(searchParams.get('q') ?? '')
  const [menuOpen, setMenuOpen] = useState(false)

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    navigate(term.trim() ? `/products?q=${encodeURIComponent(term.trim())}` : '/products')
  }

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-white/95 backdrop-blur">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 px-4 py-3">
        <Link to="/" className="text-lg font-semibold tracking-tight text-brand-700">
          Loja
        </Link>

        <nav className="order-3 flex w-full gap-4 text-sm sm:order-none sm:w-auto">
          <NavLink
            to="/products"
            className={({ isActive }) => cx('py-1 text-muted hover:text-brand-700', isActive && 'font-medium text-brand-700')}
          >
            Produtos
          </NavLink>
          {isOwner && (
            <NavLink
              to="/store"
              className={({ isActive }) => cx('py-1 text-muted hover:text-brand-700', isActive && 'font-medium text-brand-700')}
            >
              Área da loja
            </NavLink>
          )}
        </nav>

        <form onSubmit={submitSearch} className="order-4 w-full flex-1 sm:order-none sm:w-auto sm:max-w-sm">
          <Input
            type="search"
            value={term}
            onChange={(event) => setTerm(event.target.value)}
            placeholder="Buscar produtos"
            aria-label="Buscar produtos"
            className="h-10"
          />
        </form>

        <div className="ml-auto flex items-center gap-2">
          <Link
            to="/cart"
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
                      void logout().then(() => navigate('/'))
                    }}
                    className="block w-full px-4 py-2 text-left text-sm text-muted hover:bg-brand-50 hover:text-brand-700"
                  >
                    Sair
                  </button>
                </div>
              )}
            </div>
          ) : (
            <Button size="sm" onClick={() => login()} disabled={status === 'loading'}>
              Entrar
            </Button>
          )}
        </div>
      </div>
    </header>
  )
}

function MenuLink({ to, children, onClick }: { to: string; children: React.ReactNode; onClick: () => void }) {
  return (
    <Link
      to={to}
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
