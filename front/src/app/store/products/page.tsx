import { RequireAuth } from '@/components/RequireAuth'
import StoreProductListPage from '@/features/store/pages/StoreProductListPage'

export default function Page() {
  return <RequireAuth ownerOnly><StoreProductListPage /></RequireAuth>
}
