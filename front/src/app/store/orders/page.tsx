import { RequireAuth } from '@/components/RequireAuth'
import StoreOrderListPage from '@/features/store/pages/StoreOrderListPage'

export default function Page() {
  return <RequireAuth ownerOnly><StoreOrderListPage /></RequireAuth>
}
