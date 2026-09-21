import { useState } from 'react'
import { Button, Field, Input } from '../../../components/ui'

const MAX_PHOTOS = 10

export type PhotoEditorItem = { id?: number; photoUrl: string; position: number }

/**
 * Array editavel de fotos, por posicao (contrato: `position` unico, ate 10 fotos,
 * URL absoluta https). Nao lida com upload de arquivo — so URL, que e' o unico
 * modo aceito pelo `POST /products/{id}/photos` em JSON.
 *
 * So' apresenta e valida; quem chama decide o que fazer com add/remove/move
 * (grava local, no modo de criacao, ou chama a API, no modo de edicao).
 */
export function PhotoEditor({
  photos,
  onAdd,
  onRemove,
  onMove,
  busy,
}: {
  photos: PhotoEditorItem[]
  onAdd: (photoUrl: string) => void
  onRemove: (photo: PhotoEditorItem) => void
  onMove: (photo: PhotoEditorItem, direction: 'up' | 'down') => void
  busy?: boolean
}) {
  const [draftUrl, setDraftUrl] = useState('')

  const sorted = [...photos].sort((a, b) => a.position - b.position)
  const isValidUrl = /^https:\/\/\S+$/i.test(draftUrl.trim())
  const atLimit = photos.length >= MAX_PHOTOS

  function submitAdd() {
    if (!isValidUrl || atLimit || busy) return
    onAdd(draftUrl.trim())
    setDraftUrl('')
  }

  return (
    <div className="flex flex-col gap-3">
      {sorted.length === 0 && <p className="text-sm text-muted">Nenhuma foto cadastrada ainda.</p>}

      <ul className="flex flex-col gap-2">
        {sorted.map((photo, index) => (
          <li
            key={photo.id ?? photo.photoUrl}
            className="flex items-center gap-3 rounded-[8px] border border-line p-2"
          >
            <img src={photo.photoUrl} alt="" className="h-14 w-14 shrink-0 rounded-[8px] object-cover bg-brand-50" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs text-muted">{photo.photoUrl}</p>
              <p className="text-xs text-muted">Posição {photo.position}</p>
            </div>
            <div className="flex shrink-0 gap-1">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                aria-label="Mover para cima"
                disabled={busy || index === 0}
                onClick={() => onMove(photo, 'up')}
              >
                ↑
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                aria-label="Mover para baixo"
                disabled={busy || index === sorted.length - 1}
                onClick={() => onMove(photo, 'down')}
              >
                ↓
              </Button>
              <Button
                type="button"
                variant="danger"
                size="sm"
                aria-label="Remover foto"
                disabled={busy}
                onClick={() => onRemove(photo)}
              >
                Remover
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <Field
        label="Nova foto (URL)"
        hint={atLimit ? `Limite de ${MAX_PHOTOS} fotos atingido.` : 'Endereço https:// de uma imagem já hospedada.'}
        error={draftUrl && !isValidUrl ? 'Precisa ser um endereço https:// válido.' : undefined}
      >
        <div className="flex gap-2">
          <Input
            value={draftUrl}
            onChange={(event) => setDraftUrl(event.target.value)}
            aria-label="URL da nova foto"
            disabled={atLimit || busy}
          />
          <Button type="button" variant="secondary" disabled={!isValidUrl || atLimit || busy} onClick={submitAdd}>
            Adicionar
          </Button>
        </div>
      </Field>
    </div>
  )
}
