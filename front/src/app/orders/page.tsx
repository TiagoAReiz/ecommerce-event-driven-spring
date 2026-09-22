import { RequireAuth } from '@/components/RequireAuth'
import OrderListPage from '@/features/orders/pages/OrderListPage'

export default function Page() {
  return <RequireAuth><OrderListPage /></RequireAuth>
}
