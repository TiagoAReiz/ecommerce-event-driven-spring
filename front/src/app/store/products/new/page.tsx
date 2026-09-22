import { RequireAuth } from '@/components/RequireAuth'
import StoreProductFormPage from '@/features/store/pages/StoreProductFormPage'

export default function Page() {
  return <RequireAuth ownerOnly><StoreProductFormPage /></RequireAuth>
}
