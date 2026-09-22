'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  LinkButton,
  PageHeader,
  Pagination,
  Skeleton,
} from '../../../components/ui'
import { formatZipcode } from '../../../lib/format'
import { ApiError } from '../../../lib/api'
import { accountKeys, fetchAddresses, removeAddress } from '../api'
import type { Address } from '../types'

/** `/account/addresses`: lista paginada, com atalho para criar e editar. */
export default function AddressListPage() {
  const [page, setPage] = useState(0)
  const query = useQuery({ queryKey: accountKeys.addresses(page), queryFn: () => fetchAddresses(page) })

  return (
    <>
      <PageHeader
        title="Meus endereços"
        description="Usados para calcular o frete e entregar seus pedidos."
        action={<LinkButton to="/account/addresses/new">Novo endereço</LinkButton>}
      />

      {query.isPending && (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      )}

      {query.isError && (
        <ErrorState
          title="Não deu para carregar seus endereços"
          description={query.error instanceof ApiError ? query.error.message : undefined}
          onRetry={() => void query.refetch()}
        />
      )}

      {query.data && query.data.content.length === 0 && (
        <EmptyState
          title="Nenhum endereço cadastrado"
          description="Cadastre um endereço para poder finalizar uma compra."
          action={<LinkButton to="/account/addresses/new">Cadastrar endereço</LinkButton>}
        />
      )}

      {query.data && query.data.content.length > 0 && (
        <div className="flex flex-col gap-3">
          {query.data.content.map((address) => (
            <AddressRow key={address.id} address={address} />
          ))}
        </div>
      )}

      {query.data && (
        <Pagination page={page} totalPages={query.data.page.totalPages} onChange={setPage} />
      )}
    </>
  )
}

function AddressRow({ address }: { address: Address }) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)

  const mutation = useMutation({
    mutationFn: () => removeAddress(String(address.id)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['account', 'addresses'] })
    },
  })

  const error = mutation.error instanceof ApiError ? mutation.error : null
  const message = error && (error.status === 409 ? deleteConflictMessage(error) : error.message)

  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-ink">{address.name || 'Endereço'}</p>
          <p className="mt-1 text-sm text-muted">
            {address.street}, {address.number || 's/n'} — {address.city}/{address.state}
          </p>
          <p className="text-sm text-muted">
            CEP {formatZipcode(address.zipcode)} · {address.country}
          </p>
        </div>

        <div className="flex shrink-0 gap-2">
          <LinkButton to={`/account/addresses/${address.id}/edit`} variant="secondary" size="sm">
            Editar
          </LinkButton>
          {!confirming ? (
            <Button variant="danger" size="sm" onClick={() => setConfirming(true)}>
              Excluir
            </Button>
          ) : (
            <>
              <Button variant="secondary" size="sm" onClick={() => setConfirming(false)} disabled={mutation.isPending}>
                Cancelar
              </Button>
              <Button variant="danger" size="sm" loading={mutation.isPending} onClick={() => mutation.mutate()}>
                Confirmar
              </Button>
            </>
          )}
        </div>
      </div>

      {message && <p className="mt-2 text-sm text-rose-600">{message}</p>}
    </Card>
  )
}

/** So o 409 de envio em aberto e conhecido pelo contrato para este recurso. */
function deleteConflictMessage(error: ApiError): string {
  return error.message || 'Este endereço é destino de um envio ainda não finalizado.'
}
