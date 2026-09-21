import { createBrowserRouter, Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { AppLayout } from './AppLayout'
import { useAuth } from '../lib/auth'
import { EmptyState, LinkButton, PageHeader, Spinner } from '../components/ui'

import HomePage from '../features/catalog/pages/HomePage'
import ProductListPage from '../features/catalog/pages/ProductListPage'
import ProductDetailPage from '../features/catalog/pages/ProductDetailPage'

import LoginPage from '../features/account/pages/LoginPage'
import CallbackPage from '../features/account/pages/CallbackPage'
import ProfilePage from '../features/account/pages/ProfilePage'
import AddressListPage from '../features/account/pages/AddressListPage'
import AddressFormPage from '../features/account/pages/AddressFormPage'

import CartPage from '../features/checkout/pages/CartPage'
import CheckoutPage from '../features/checkout/pages/CheckoutPage'
import PaymentPage from '../features/checkout/pages/PaymentPage'
import OrderPlacedPage from '../features/checkout/pages/OrderPlacedPage'

import OrderListPage from '../features/orders/pages/OrderListPage'
import OrderDetailPage from '../features/orders/pages/OrderDetailPage'
import PendingReviewsPage from '../features/orders/pages/PendingReviewsPage'
import MyReviewsPage from '../features/orders/pages/MyReviewsPage'

import StoreHomePage from '../features/store/pages/StoreHomePage'
import StoreProductListPage from '../features/store/pages/StoreProductListPage'
import StoreProductFormPage from '../features/store/pages/StoreProductFormPage'
import StoreOrderListPage from '../features/store/pages/StoreOrderListPage'
import StoreShipmentListPage from '../features/store/pages/StoreShipmentListPage'

/**
 * Rota que exige sessao. Enquanto o token guardado nao foi conferido com o
 * gateway o estado e `loading`: mandar para o login aqui deslogaria quem
 * apenas atualizou a pagina.
 */
function RequireAuth({ children, ownerOnly }: { children: ReactNode; ownerOnly?: boolean }) {
  const { status, isOwner } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="flex justify-center py-24">
        <Spinner className="h-8 w-8 text-brand-700" />
      </div>
    )
  }

  if (status === 'anonymous') {
    // O destino vai junto para o login devolver o usuario onde ele estava.
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
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

function NotFoundPage() {
  return (
    <>
      <PageHeader title="Página não encontrada" />
      <EmptyState
        title="Este endereço não existe"
        description="O link pode estar errado ou a página pode ter saído do ar."
        action={<LinkButton to="/">Ir para a vitrine</LinkButton>}
      />
    </>
  )
}

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      // Vitrine: publica, sem token.
      { path: '/', element: <HomePage /> },
      { path: '/products', element: <ProductListPage /> },
      { path: '/products/:id', element: <ProductDetailPage /> },

      // Login. O gateway devolve o token em /callback#token=... — nao mude este caminho.
      { path: '/login', element: <LoginPage /> },
      { path: '/callback', element: <CallbackPage /> },

      // Conta do comprador.
      { path: '/account', element: <RequireAuth><ProfilePage /></RequireAuth> },
      { path: '/account/addresses', element: <RequireAuth><AddressListPage /></RequireAuth> },
      { path: '/account/addresses/new', element: <RequireAuth><AddressFormPage /></RequireAuth> },
      { path: '/account/addresses/:id/edit', element: <RequireAuth><AddressFormPage /></RequireAuth> },

      // Carrinho e compra.
      { path: '/cart', element: <RequireAuth><CartPage /></RequireAuth> },
      { path: '/checkout', element: <RequireAuth><CheckoutPage /></RequireAuth> },
      { path: '/checkout/payment/:orderId', element: <RequireAuth><PaymentPage /></RequireAuth> },
      { path: '/checkout/done/:orderId', element: <RequireAuth><OrderPlacedPage /></RequireAuth> },

      // Pedidos, envios e avaliacoes do comprador.
      { path: '/orders', element: <RequireAuth><OrderListPage /></RequireAuth> },
      { path: '/orders/:id', element: <RequireAuth><OrderDetailPage /></RequireAuth> },
      { path: '/reviews/pending', element: <RequireAuth><PendingReviewsPage /></RequireAuth> },
      { path: '/reviews/mine', element: <RequireAuth><MyReviewsPage /></RequireAuth> },

      // Area da loja: so o dono.
      { path: '/store', element: <RequireAuth ownerOnly><StoreHomePage /></RequireAuth> },
      { path: '/store/products', element: <RequireAuth ownerOnly><StoreProductListPage /></RequireAuth> },
      { path: '/store/products/new', element: <RequireAuth ownerOnly><StoreProductFormPage /></RequireAuth> },
      { path: '/store/products/:id', element: <RequireAuth ownerOnly><StoreProductFormPage /></RequireAuth> },
      { path: '/store/orders', element: <RequireAuth ownerOnly><StoreOrderListPage /></RequireAuth> },
      { path: '/store/shipments', element: <RequireAuth ownerOnly><StoreShipmentListPage /></RequireAuth> },

      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
