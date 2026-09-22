import { RequireAuth } from '@/components/RequireAuth'
import AddressListPage from '@/features/account/pages/AddressListPage'

export default function Page() {
  return <RequireAuth><AddressListPage /></RequireAuth>
}
