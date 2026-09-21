import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Badge,
  Button,
  Card,
  ErrorState,
  Field,
  Input,
  LinkButton,
  PageHeader,
  Skeleton,
} from '../../../components/ui'
import { date, onlyDigits } from '../../../lib/format'
import { ApiError } from '../../../lib/api'
import { useAuth } from '../../../lib/auth'
import { accountKeys, deleteMe, fetchMe, updateMe } from '../api'
import type { ProfilePatch, UserProfile } from '../types'
import { isValidCpfDigits, isValidPhone } from '../validation'

/** `/account`: ver e editar o perfil, com atalhos para o resto da conta. */
export default function ProfilePage() {
  const query = useQuery({ queryKey: accountKeys.me, queryFn: fetchMe })

  return (
    <>
      <PageHeader title="Minha conta" description="Seus dados, endereços e pedidos." />

      {query.isPending && <Skeleton className="h-64 w-full" />}

      {query.isError && (
        <ErrorState
          title="Não deu para carregar seu perfil"
          description={query.error instanceof ApiError ? query.error.message : undefined}
          onRetry={() => void query.refetch()}
        />
      )}

      {query.data && <ProfileContent profile={query.data} />}
    </>
  )
}

function ProfileContent({ profile }: { profile: UserProfile }) {
  return (
    <div className="flex flex-col gap-6 pb-10">
      <ProfileCard profile={profile} />
      <ShortcutsCard addressCount={profile.addressCount} />
      <DangerCard />
    </div>
  )
}

function ProfileCard({ profile }: { profile: UserProfile }) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(profile.name)
  const [cpf, setCpf] = useState(profile.cpf ?? '')
  const [phone, setPhone] = useState(profile.phone ?? '')
  const [localErrors, setLocalErrors] = useState<Record<string, string>>({})

  const mutation = useMutation({
    mutationFn: (body: ProfilePatch) => updateMe(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: accountKeys.me })
      setEditing(false)
    },
  })

  function startEdit() {
    setName(profile.name)
    setCpf(profile.cpf ?? '')
    setPhone(profile.phone ?? '')
    setLocalErrors({})
    mutation.reset()
    setEditing(true)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const errors: Record<string, string> = {}

    const trimmedName = name.trim()
    if (!trimmedName) errors.name = 'informe um nome'

    const cpfDigits = onlyDigits(cpf)
    if (cpfDigits && !isValidCpfDigits(cpfDigits)) errors.cpf = 'CPF precisa ter 11 dígitos'

    const trimmedPhone = phone.trim()
    if (trimmedPhone && !isValidPhone(trimmedPhone)) {
      errors.phone = 'use o formato +5511999998888'
    }

    setLocalErrors(errors)
    if (Object.keys(errors).length > 0) return

    // So manda o que mudou: PATCH aceita corpo parcial, mas nao vazio.
    const body: ProfilePatch = {}
    if (trimmedName !== profile.name) body.name = trimmedName
    if (cpfDigits !== (profile.cpf ?? '')) body.cpf = cpfDigits
    if (trimmedPhone !== (profile.phone ?? '')) body.phone = trimmedPhone
    if (Object.keys(body).length === 0) {
      setEditing(false)
      return
    }

    mutation.mutate(body)
  }

  const apiErrors = mutation.error instanceof ApiError ? mutation.error.fieldErrors : {}
  const hasFieldErrors = Object.keys(apiErrors).length > 0
  const fieldError = (field: string) => localErrors[field] ?? apiErrors[field]

  if (!editing) {
    return (
      <Card className="p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-center gap-4">
            {profile.photoUrl ? (
              <img src={profile.photoUrl} alt="" className="h-16 w-16 rounded-full object-cover" />
            ) : (
              <span className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-100 text-xl font-medium text-brand-700">
                {profile.name.slice(0, 1).toUpperCase()}
              </span>
            )}
            <div>
              <p className="text-lg font-semibold text-ink">{profile.name}</p>
              <p className="text-sm text-muted">{profile.email}</p>
              {profile.roles.includes('owner') && <Badge tone="info">Dono da loja</Badge>}
            </div>
          </div>
          <Button variant="secondary" size="sm" onClick={startEdit}>
            Editar
          </Button>
        </div>

        <dl className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt className="text-xs text-muted">CPF</dt>
            <dd className="text-sm text-ink">{profile.cpf ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Telefone</dt>
            <dd className="text-sm text-ink">{profile.phone ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Cliente desde</dt>
            <dd className="text-sm text-ink">{date(profile.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Endereços cadastrados</dt>
            <dd className="text-sm text-ink">{profile.addressCount}</dd>
          </div>
        </dl>
      </Card>
    )
  }

  return (
    <Card className="p-6">
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field label="Nome" required error={fieldError('name')}>
          <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={120} />
        </Field>
        <Field label="CPF" hint="só números, ex.: 12345678901" error={fieldError('cpf')}>
          <Input
            value={cpf}
            onChange={(event) => setCpf(event.target.value)}
            inputMode="numeric"
            maxLength={14}
          />
        </Field>
        <Field label="Telefone" hint="com código do país, ex.: +5511999998888" error={fieldError('phone')}>
          <Input value={phone} onChange={(event) => setPhone(event.target.value)} />
        </Field>

        {mutation.isError && !hasFieldErrors && (
          <p className="text-sm text-rose-600">
            {mutation.error instanceof ApiError ? mutation.error.message : 'Não deu para salvar.'}
          </p>
        )}

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => setEditing(false)} disabled={mutation.isPending}>
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

function ShortcutsCard({ addressCount }: { addressCount: number }) {
  return (
    <Card className="grid grid-cols-1 divide-y divide-line sm:grid-cols-3 sm:divide-x sm:divide-y-0">
      <ShortcutLink to="/account/addresses" title="Endereços" description={`${addressCount} cadastrado(s)`} />
      <ShortcutLink to="/orders" title="Meus pedidos" description="Acompanhe suas compras" />
      <ShortcutLink to="/reviews/pending" title="Avaliações" description="O que falta avaliar" />
    </Card>
  )
}

function ShortcutLink({ to, title, description }: { to: string; title: string; description: string }) {
  return (
    <LinkButton to={to} variant="ghost" className="h-auto flex-col items-start gap-1 rounded-none px-5 py-4 text-left">
      <span className="text-sm font-medium text-ink">{title}</span>
      <span className="text-xs text-muted">{description}</span>
    </LinkButton>
  )
}

function DangerCard() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)

  const mutation = useMutation({
    mutationFn: deleteMe,
    onSuccess: () => {
      void logout().then(() => navigate('/'))
    },
  })

  const error = mutation.error instanceof ApiError ? mutation.error : null
  const message = deleteErrorMessage(error)

  return (
    <Card className="border-rose-200 p-6">
      <p className="text-sm font-medium text-ink">Excluir conta</p>
      <p className="mt-1 text-sm text-muted">
        Remove sua conta e seus dados. Não é possível desfazer.
      </p>

      {message && <p className="mt-3 text-sm text-rose-600">{message}</p>}

      {!confirming ? (
        <Button variant="danger" size="sm" className="mt-4" onClick={() => setConfirming(true)}>
          Excluir minha conta
        </Button>
      ) : (
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <p className="text-sm text-ink">Tem certeza? Essa ação não pode ser desfeita.</p>
          <div className="ml-auto flex gap-3">
            <Button variant="secondary" size="sm" onClick={() => setConfirming(false)} disabled={mutation.isPending}>
              Cancelar
            </Button>
            <Button variant="danger" size="sm" loading={mutation.isPending} onClick={() => mutation.mutate()}>
              Sim, excluir
            </Button>
          </div>
        </div>
      )}
    </Card>
  )
}

/** Mensagem por `code`: so o `STORE_OWNER_ACCOUNT` e conhecido, o resto usa o texto do backend. */
function deleteErrorMessage(error: ApiError | null): string | null {
  if (!error) return null
  if (error.status !== 409) return error.message
  if (error.code === 'STORE_OWNER_ACCOUNT') {
    return 'A conta da loja não pode ser removida pela API.'
  }
  return error.message || 'Há um pedido em aberto: finalize-o antes de excluir a conta.'
}
