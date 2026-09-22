import { RequireAuth } from '@/components/RequireAuth'
import StoreShipmentListPage from '@/features/store/pages/StoreShipmentListPage'

export default function Page() {
  return <RequireAuth ownerOnly><StoreShipmentListPage /></RequireAuth>
}
