import { RequireAuth } from '@/components/RequireAuth'
import ProfilePage from '@/features/account/pages/ProfilePage'

export default function Page() {
  return <RequireAuth><ProfilePage /></RequireAuth>
}
