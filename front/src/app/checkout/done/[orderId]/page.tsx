import { RequireAuth } from '@/components/RequireAuth'
import OrderPlacedPage from '@/features/checkout/pages/OrderPlacedPage'

export default async function Page({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = await params
  return <RequireAuth><OrderPlacedPage orderId={orderId} /></RequireAuth>
}
