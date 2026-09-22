import { RequireAuth } from '@/components/RequireAuth'
import StoreHomePage from '@/features/store/pages/StoreHomePage'

export default function Page() {
  return <RequireAuth ownerOnly><StoreHomePage /></RequireAuth>
}
