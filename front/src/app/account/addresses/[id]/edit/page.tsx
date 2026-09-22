import { RequireAuth } from '@/components/RequireAuth'
import AddressFormPage from '@/features/account/pages/AddressFormPage'

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return <RequireAuth><AddressFormPage id={id} /></RequireAuth>
}
