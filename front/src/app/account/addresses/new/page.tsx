import { RequireAuth } from '@/components/RequireAuth'
import AddressFormPage from '@/features/account/pages/AddressFormPage'

export default function Page() {
  return <RequireAuth><AddressFormPage /></RequireAuth>
}
