import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, ErrorState, Field, Input, PageHeader, Select, Skeleton } from '../../../components/ui'
import { formatZipcode, onlyDigits } from '../../../lib/format'
import { ApiError } from '../../../lib/api'
import { accountKeys, createAddress, fetchAddress, replaceAddress } from '../api'
import type { AddressInput } from '../types'
import { BRAZIL_STATES, isValidZipcodeDigits } from '../validation'

type FormValues = {
  name: string
  zipcodeDigits: string
  country: string
  state: string
  city: string
  street: string
  number: string
}

const EMPTY_FORM: FormValues = {
  name: '',
  zipcodeDigits: '',
  country: 'BR',
  state: '',
  city: '',
  street: '',
  number: '',
}

/** Serve `/account/addresses/new` e `/account/addresses/:id/edit`, decidido pelo `id` da rota. */
export default function AddressFormPage() {
  const { id } = useParams<{ id: string }>()
  const isEdit = Boolean(id)

  const detailQuery = useQuery({
    queryKey: id ? accountKeys.address(id) : ['account', 'addresses', 'detail', 'new'],
    queryFn: () => fetchAddress(id as string),
    enabled: isEdit,
  })

  return (
    <>
      <PageHeader title={isEdit ? 'Editar endereço' : 'Novo endereço'} />

      {isEdit && detailQuery.isPending && <Skeleton className="h-96 w-full" />}

      {isEdit && detailQuery.isError && (
        <ErrorState
          title="Não deu para carregar este endereço"
          description={detailQuery.error instanceof ApiError ? detailQuery.error.message : undefined}
          onRetry={() => void detailQuery.refetch()}
        />
      )}

      {(!isEdit || detailQuery.data) && (
        <AddressForm
          id={id}
          initial={
            detailQuery.data
              ? {
                  name: detailQuery.data.name ?? '',
                  zipcodeDigits: detailQuery.data.zipcode,
                  country: detailQuery.data.country,
                  state: detailQuery.data.state,
                  city: detailQuery.data.city,
                  street: detailQuery.data.street,
                  number: detailQuery.data.number ?? '',
                }
              : EMPTY_FORM
          }
        />
      )}
    </>
  )
}

function AddressForm({ id, initial }: { id?: string; initial: FormValues }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [values, setValues] = useState<FormValues>(initial)
  const [localErrors, setLocalErrors] = useState<Record<string, string>>({})

  // Quando o endereco carrega (edicao), preenche o formulario uma vez.
  useEffect(() => setValues(initial), [initial])

  const mutation = useMutation({
    mutationFn: (body: AddressInput) => (id ? replaceAddress(id, body) : createAddress(body)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['account', 'addresses'] })
      navigate('/account/addresses')
    },
  })

  function setField<K extends keyof FormValues>(key: K, value: FormValues[K]) {
    setValues((current) => ({ ...current, [key]: value }))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const errors: Record<string, string> = {}

    if (!isValidZipcodeDigits(values.zipcodeDigits)) errors.zipcode = 'CEP precisa ter 8 dígitos'
    if (!values.state.trim()) errors.state = 'informe o estado'
    if (!values.city.trim()) errors.city = 'informe a cidade'
    if (!values.street.trim()) errors.street = 'informe a rua'
    if (!values.country.trim()) errors.country = 'informe o país'

    setLocalErrors(errors)
    if (Object.keys(errors).length > 0) return

    const body: AddressInput = {
      name: values.name.trim() || undefined,
      zipcode: values.zipcodeDigits,
      country: values.country.trim().toUpperCase(),
      state: values.state.trim().toUpperCase(),
      city: values.city.trim(),
      street: values.street.trim(),
      number: values.number.trim() || undefined,
    }

    mutation.mutate(body)
  }

  const apiErrors = mutation.error instanceof ApiError ? mutation.error.fieldErrors : {}
  const hasFieldErrors = Object.keys(apiErrors).length > 0
  const fieldError = (field: string) => localErrors[field] ?? apiErrors[field]

  return (
    <Card className="max-w-xl p-6">
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field label="Nome do endereço" hint="opcional, ex.: casa, trabalho" error={fieldError('name')}>
          <Input
            value={values.name}
            onChange={(event) => setField('name', event.target.value)}
            maxLength={60}
          />
        </Field>

        <Field label="CEP" required hint="ex.: 01310-100" error={fieldError('zipcode')}>
          <Input
            value={formatZipcode(values.zipcodeDigits)}
            onChange={(event) => setField('zipcodeDigits', onlyDigits(event.target.value).slice(0, 8))}
            inputMode="numeric"
          />
        </Field>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Estado (UF)" required error={fieldError('state')}>
            <Select value={values.state} onChange={(event) => setField('state', event.target.value)}>
              <option value="">selecione</option>
              {BRAZIL_STATES.map((uf) => (
                <option key={uf} value={uf}>
                  {uf}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="País" required hint="ISO-3166, ex.: BR" error={fieldError('country')}>
            <Input
              value={values.country}
              onChange={(event) => setField('country', event.target.value.toUpperCase().slice(0, 2))}
              maxLength={2}
            />
          </Field>
        </div>

        <Field label="Cidade" required error={fieldError('city')}>
          <Input value={values.city} onChange={(event) => setField('city', event.target.value)} />
        </Field>

        <Field label="Rua" required error={fieldError('street')}>
          <Input value={values.street} onChange={(event) => setField('street', event.target.value)} />
        </Field>

        <Field label="Número" hint='opcional — "s/n" é aceito' error={fieldError('number')}>
          <Input value={values.number} onChange={(event) => setField('number', event.target.value)} />
        </Field>

        {mutation.isError && !hasFieldErrors && (
          <p className="text-sm text-rose-600">
            {mutation.error instanceof ApiError ? mutation.error.message : 'Não deu para salvar o endereço.'}
          </p>
        )}

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate('/account/addresses')} disabled={mutation.isPending}>
            Cancelar
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            Salvar
          </Button>
        </div>
      </form>
    </Card>
  )
}
