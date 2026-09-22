'use client'

import { useState } from 'react'
import { Button, Field, Textarea } from '@/components/ui'
import { ApiError } from '@/lib/api'
import { Modal } from './Modal'

const REASON_MAX = 500
const REASON_MIN = 5

/** Modal generico de "motivo obrigatorio": usado no cancelamento de pedido e no de
 * envio, que pedem a mesma coisa (motivo, ate 500 caracteres — limite `VARCHAR`
 * do contrato) com endpoints diferentes por tras. */
export function ReasonModal({
  title,
  description,
  confirmLabel,
  danger,
  onClose,
  onConfirm,
  isPending,
  error,
}: {
  title: string
  description?: string
  confirmLabel: string
  danger?: boolean
  onClose: () => void
  onConfirm: (reason: string) => void
  isPending: boolean
  error: unknown
}) {
  const [reason, setReason] = useState('')

  const overLimit = reason.length > REASON_MAX
  const tooShort = reason.trim().length > 0 && reason.trim().length < REASON_MIN
  const empty = reason.trim().length === 0
  const invalid = empty || overLimit || tooShort

  function submit() {
    if (invalid || isPending) return
    onConfirm(reason.trim())
  }

  const conflictMessage =
    error instanceof ApiError && (error.status === 409 || error.status === 422)
      ? error.problem.detail ?? 'Essa transição não é permitida agora.'
      : null
  const genericMessage = error instanceof ApiError && !conflictMessage ? error.message : null

  return (
    <Modal title={title} onClose={onClose}>
      <div className="flex flex-col gap-4">
        {description && <p className="text-sm text-muted">{description}</p>}

        <Field
          label="Motivo"
          required
          error={overLimit ? `Máximo de ${REASON_MAX} caracteres.` : tooShort ? `Mínimo de ${REASON_MIN} caracteres.` : undefined}
          hint={overLimit || tooShort ? undefined : `${reason.length}/${REASON_MAX}`}
        >
          <Textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={4}
            aria-label="Motivo"
            maxLength={REASON_MAX + 50}
          />
        </Field>

        {conflictMessage && <p className="rounded-[8px] bg-brand-50 px-3 py-2 text-sm text-ink">{conflictMessage}</p>}
        {genericMessage && <p className="text-sm text-ink">{genericMessage}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={isPending}>
            Voltar
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={submit} loading={isPending} disabled={invalid}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
