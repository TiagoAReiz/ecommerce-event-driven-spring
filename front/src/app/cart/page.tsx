import { RequireAuth } from '@/components/RequireAuth'
import CartPage from '@/features/checkout/pages/CartPage'

export default function Page() {
  return <RequireAuth><CartPage /></RequireAuth>
}
