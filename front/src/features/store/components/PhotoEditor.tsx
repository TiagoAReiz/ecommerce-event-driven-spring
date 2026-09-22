'use client'

import { useState } from 'react'
import { Button, Field, Input } from '@/components/ui'
import { ApiError } from '@/lib/api'
import { cx } from '@/lib/format'
import { errorDescription, errorTitle } from '../errors'
import type { PhotoUploadUrlResponse } from '../types'

const MAX_PHOTOS = 10

// mesmo limite que o backend aplica (contrato da rota de upload-url): checar aqui
// evita gastar uma viagem para o servidor so' para ouvir "nao".
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/avif']
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024

export type PhotoEditorItem = { id?: number; photoUrl: string; position: number }

function validateFile(file: File): string | null {
  if (!ACCEPTED_TYPES.includes(file.type)) return 'Formato não aceito: use JPEG, PNG, WEBP ou AVIF.'
  if (file.size > MAX_UPLOAD_BYTES) return 'Arquivo maior que 5 MB.'
  return null
}

/** PUT direto ao S3 com a URL assinada. Usa XMLHttpRequest (nao `fetch`) so' por
 * causa do `upload.onprogress`: e' o unico jeito de mostrar barra de progresso de
 * verdade no navegador. Sem `Authorization` de proposito — a assinatura da URL e'
 * a autorizacao, e mandar o Bearer do usuario aqui nao faz sentido (e quebraria a
 * assinatura se fosse via `api`, que sempre acrescenta o header). */
function putFileToS3(uploadUrl: string, file: File, onProgress: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', uploadUrl)
    xhr.setRequestHeader('Content-Type', file.type)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100))
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve()
      else reject(new Error(`O servidor de arquivos recusou o envio (código ${xhr.status}).`))
    }
    xhr.onerror = () => reject(new Error('Falha de rede ao enviar o arquivo.'))
    xhr.send(file)
  })
}

/**
 * Array editavel de fotos, por posicao (contrato: `position` unico, ate 10 fotos,
 * URL absoluta https). So' apresenta e valida a lista; quem chama decide o que
 * fazer com add/remove/move (grava local, no modo de criacao, ou chama a API, no
 * modo de edicao).
 *
 * Envio de arquivo e' a excecao: como o PUT ao S3 nao passa pelo `api` (sem Bearer,
 * com barra de progresso via XHR), esse pedaco mora aqui dentro. Quem chama so'
 * entra com `onRequestUploadUrl` (o `POST /products/{id}/photos/upload-url`, que
 * precisa de id de produto — por isso e' opcional: no modo de criacao, sem produto
 * ainda, essa opcao some e sobra so' colar URL).
 */
export function PhotoEditor({
  photos,
  onAdd,
  onRemove,
  onMove,
  onRequestUploadUrl,
  busy,
}: {
  photos: PhotoEditorItem[]
  onAdd: (photoUrl: string) => void
  onRemove: (photo: PhotoEditorItem) => void
  onMove: (photo: PhotoEditorItem, direction: 'up' | 'down') => void
  onRequestUploadUrl?: (file: File) => Promise<PhotoUploadUrlResponse>
  busy?: boolean
}) {
  const [draftUrl, setDraftUrl] = useState('')
  const [isDragging, setIsDragging] = useState(false)
  const [uploadPreview, setUploadPreview] = useState<string | null>(null)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)

  const sorted = [...photos].sort((a, b) => a.position - b.position)
  const isValidUrl = /^https:\/\/\S+$/i.test(draftUrl.trim())
  const atLimit = photos.length >= MAX_PHOTOS
  const uploadDisabled = !onRequestUploadUrl || atLimit || busy || uploading

  function submitAdd() {
    if (!isValidUrl || atLimit || busy) return
    onAdd(draftUrl.trim())
    setDraftUrl('')
  }

  async function handleFile(file: File) {
    if (uploadDisabled || !onRequestUploadUrl) return

    const validationError = validateFile(file)
    if (validationError) {
      setUploadError(validationError)
      return
    }

    setUploadError(null)
    const previewUrl = URL.createObjectURL(file)
    setUploadPreview(previewUrl)
    setUploading(true)
    setUploadProgress(0)

    // guarda a etapa para a mensagem de erro dizer o que exatamente falhou.
    let stage: 'prepare' | 'upload' = 'prepare'
    try {
      const { uploadUrl, publicUrl } = await onRequestUploadUrl(file)
      stage = 'upload'
      await putFileToS3(uploadUrl, file, setUploadProgress)
      onAdd(publicUrl)
    } catch (err) {
      const prefix = stage === 'prepare' ? 'Não foi possível preparar o envio: ' : 'Não foi possível enviar o arquivo: '
      const detail =
        err instanceof ApiError ? (errorDescription(err) ?? errorTitle(err)) : err instanceof Error ? err.message : 'erro desconhecido.'
      setUploadError(prefix + detail)
    } finally {
      setUploading(false)
      setUploadProgress(0)
      URL.revokeObjectURL(previewUrl)
      setUploadPreview(null)
    }
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

      {onRequestUploadUrl && (
        <Field
          label="Enviar arquivo"
          hint={atLimit ? `Limite de ${MAX_PHOTOS} fotos atingido.` : 'JPEG, PNG, WEBP ou AVIF, até 5 MB.'}
        >
          <label
            className={cx(
              'flex flex-col items-center justify-center gap-2 rounded-[8px] border border-dashed p-4 text-center text-sm transition-colors',
              isDragging ? 'border-brand-600 bg-brand-50' : 'border-line',
              uploadDisabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:bg-brand-50',
            )}
            onDragOver={(event) => {
              event.preventDefault()
              if (!uploadDisabled) setIsDragging(true)
            }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={(event) => {
              event.preventDefault()
              setIsDragging(false)
              if (uploadDisabled) return
              const file = event.dataTransfer.files?.[0]
              if (file) void handleFile(file)
            }}
          >
            <input
              type="file"
              accept={ACCEPTED_TYPES.join(',')}
              className="sr-only"
              disabled={uploadDisabled}
              onChange={(event) => {
                const file = event.target.files?.[0]
                // limpa o valor: sem isso, escolher o mesmo arquivo de novo nao dispara onChange.
                event.target.value = ''
                if (file) void handleFile(file)
              }}
            />
            {uploading ? (
              <>
                {uploadPreview && (
                  <img src={uploadPreview} alt="" className="h-16 w-16 rounded-[8px] object-cover" />
                )}
                <div className="w-full max-w-xs">
                  <div className="h-2 w-full overflow-hidden rounded-full bg-brand-50">
                    <div className="h-full bg-brand-700 transition-all" style={{ width: `${uploadProgress}%` }} />
                  </div>
                  <p className="mt-1 text-xs text-muted">Enviando... {uploadProgress}%</p>
                </div>
              </>
            ) : (
              <span className="text-ink">Arraste uma foto aqui ou toque para escolher</span>
            )}
          </label>
        </Field>
      )}
      {uploadError && <p className="text-xs text-rose-600">{uploadError}</p>}

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
