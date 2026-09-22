import { RequireAuth } from '@/components/RequireAuth'
import StoreProductFormPage from '@/features/store/pages/StoreProductFormPage'

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return <RequireAuth ownerOnly><StoreProductFormPage id={id} /></RequireAuth>
}
