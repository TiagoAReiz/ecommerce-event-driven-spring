import { RequireAuth } from '@/components/RequireAuth'
import OrderDetailPage from '@/features/orders/pages/OrderDetailPage'

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return <RequireAuth><OrderDetailPage id={id} /></RequireAuth>
}
