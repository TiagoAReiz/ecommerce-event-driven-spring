import { RequireAuth } from '@/components/RequireAuth'
import MyReviewsPage from '@/features/orders/pages/MyReviewsPage'

export default function Page() {
  return <RequireAuth><MyReviewsPage /></RequireAuth>
}
