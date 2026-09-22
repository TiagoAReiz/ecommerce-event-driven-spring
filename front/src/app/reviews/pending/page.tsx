import { RequireAuth } from '@/components/RequireAuth'
import PendingReviewsPage from '@/features/orders/pages/PendingReviewsPage'

export default function Page() {
  return <RequireAuth><PendingReviewsPage /></RequireAuth>
}
