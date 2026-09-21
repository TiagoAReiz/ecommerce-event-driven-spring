import { useQuery } from '@tanstack/react-query'
import { Button, Card, EmptyState, ErrorState, LinkButton, Skeleton } from '../../../components/ui'
import { formatZipcode } from '../../../lib/format'
import { ApiError } from '../../../lib/api'
import { fetchAddresses } from '../../account/api'
import type { Address } from '../../account/types'

/** Etapa 1: escolher o endereco de entrega. `GET /users/me/addresses`, so a primeira pagina. */
export function AddressStep({
  selectedId,
  onSelect,
  onContinue,
}: {
  selectedId: number | null
  onSelect: (address: Address) => void
  onContinue: () => void
}) {
  const addressQuery = useQuery({
    queryKey: ['checkout', 'addresses'],
    queryFn: () => fetchAddresses(0),
  })

  if (addressQuery.isLoading) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    )
  }

  if (addressQuery.isError) {
    return (
      <ErrorState
        title="Não deu para carregar seus endereços"
        description={addressQuery.error instanceof ApiError ? addressQuery.error.message : undefined}
        onRetry={() => void addressQuery.refetch()}
      />
    )
  }

  const addresses = addressQuery.data?.content ?? []

  if (addresses.length === 0) {
    return (
      <EmptyState
        title="Você ainda não tem endereços"
        description="Cadastre um endereço de entrega para continuar a compra."
        action={<LinkButton to="/account/addresses/new">Novo endereço</LinkButton>}
      />
    )
  }

  return (
    <div className="flex flex-col gap-3">
      <fieldset className="flex flex-col gap-3">
        <legend className="sr-only">Endereço de entrega</legend>
        {addresses.map((address) => (
          <label key={address.id} className="block cursor-pointer">
            <Card
              className={`flex items-start gap-3 p-4 ${selectedId === address.id ? 'border-brand-600 ring-1 ring-brand-600' : ''}`}
            >
              <input
                type="radio"
                name="address"
                className="mt-1"
                checked={selectedId === address.id}
                onChange={() => onSelect(address)}
                aria-label={address.name ?? `Endereço em ${address.city}`}
              />
              <div className="min-w-0">
                {address.name && <p className="text-sm font-medium text-ink">{address.name}</p>}
                <p className="text-sm text-ink">
                  {address.street}
                  {address.number ? `, ${address.number}` : ''}
                </p>
                <p className="text-sm text-muted">
                  {address.city}/{address.state} · {formatZipcode(address.zipcode)}
                </p>
              </div>
            </Card>
          </label>
        ))}
      </fieldset>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <LinkButton to="/account/addresses/new" variant="secondary" size="sm">
          Novo endereço
        </LinkButton>
        <Button disabled={selectedId === null} onClick={onContinue}>
          Continuar
        </Button>
      </div>
    </div>
  )
}
