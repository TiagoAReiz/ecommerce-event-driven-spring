import { RequireAuth } from '@/components/RequireAuth'
import PaymentPage from '@/features/checkout/pages/PaymentPage'

export default async function Page({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = await params
  return <RequireAuth><PaymentPage orderId={orderId} /></RequireAuth>
}
