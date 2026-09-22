'use client'

import { useState } from 'react'
import { Button, Field, Input, Textarea } from '@/components/ui'
import { cx } from '@/lib/format'
import { Modal } from './Modal'

export type ReviewFormValues = { rate: number; title: string; description: string }

/**
 * Formulario de avaliacao, reaproveitado para criar (avaliacoes pendentes) e
 * editar (minhas avaliacoes). Quem chama decide a mutation e traduz o erro.
 */
export function ReviewFormModal({
  title,
  productName,
  initialValues,
  submitLabel,
  isPending,
  errorMessage,
  fieldErrors,
  onSubmit,
  onClose,
}: {
  title: string
  productName: string
  initialValues?: Partial<ReviewFormValues>
  submitLabel: string
  isPending: boolean
  errorMessage?: string | null
  fieldErrors?: Record<string, string>
  onSubmit: (values: ReviewFormValues) => void
  onClose: () => void
}) {
  const [rate, setRate] = useState(initialValues?.rate ?? 0)
  const [reviewTitle, setReviewTitle] = useState(initialValues?.title ?? '')
  const [description, setDescription] = useState(initialValues?.description ?? '')

  const rateError = rate < 1 || rate > 5 ? 'Escolha de 1 a 5 estrelas.' : undefined

  function submit() {
    if (rate < 1 || rate > 5 || isPending) return
    onSubmit({ rate, title: reviewTitle.trim(), description: description.trim() })
  }

  return (
    <Modal title={title} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-sm font-medium text-ink">{productName}</p>

        <Field label="Nota" required error={fieldErrors?.rate ?? rateError}>
          <div className="flex gap-1" role="radiogroup" aria-label="Nota de 1 a 5 estrelas">
            {[1, 2, 3, 4, 5].map((value) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={rate === value}
                aria-label={`${value} estrela${value > 1 ? 's' : ''}`}
                onClick={() => setRate(value)}
                className={cx(
                  'h-10 w-10 rounded-[8px] border text-lg',
                  value <= rate
                    ? 'border-brand-600 bg-brand-50 text-brand-700'
                    : 'border-line bg-white text-muted',
                )}
              >
                ★
              </button>
            ))}
          </div>
        </Field>

        <Field label="Título" hint="Opcional, até 150 caracteres" error={fieldErrors?.title}>
          <Input
            value={reviewTitle}
            onChange={(event) => setReviewTitle(event.target.value)}
            maxLength={150}
          />
        </Field>

        <Field label="Comentário" hint="Opcional" error={fieldErrors?.description}>
          <Textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={4}
          />
        </Field>

        {errorMessage && <p className="rounded-[8px] bg-brand-50 px-3 py-2 text-sm text-ink">{errorMessage}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={isPending}>
            Voltar
          </Button>
          <Button onClick={submit} loading={isPending} disabled={rate < 1 || rate > 5}>
            {submitLabel}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
