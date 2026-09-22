import { RequireAuth } from '@/components/RequireAuth'
import CheckoutPage from '@/features/checkout/pages/CheckoutPage'

export default function Page() {
  return <RequireAuth><CheckoutPage /></RequireAuth>
}
