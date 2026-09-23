'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { formatarPreco, limparPreco } from '../preco'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Input,
  PageHeader,
  Select,
  Skeleton,
  Textarea,
} from '@/components/ui'
import { dateTime, money } from '@/lib/format'
import { errorDescription, errorTitle } from '../errors'
import {
  useAddPhoto,
  useCategories,
  useCreateProduct,
  useDeletePhoto,
  useDeleteProduct,
  useManageProduct,
  usePatchProduct,
  useReorderPhotos,
  useRequestDraftPhotoUploadUrl,
  useRequestPhotoUploadUrl,
  useUpdateStock,
} from '../queries'
import { PhotoEditor } from '../components/PhotoEditor'
import type { PhotoEditorItem } from '../components/PhotoEditor'

const PRICE_PATTERN = /^\d+\.\d{2}$/

/** Serve as duas rotas de produto: `/store/products/new` (`id` ausente, cria) e
 * `/store/products/:id` (edita). Preco entra e sai como string com 2 casas — o
 * contrato nunca aceita number, pra nao perder centavo em arredondamento.
 * `id` chega por prop, vindo do segmento dinamico da rota (App Router) — ausente
 * na rota `/store/products/new`, presente em `/store/products/[id]`. */
export default function StoreProductFormPage({ id: idParam }: { id?: string }) {
  const isEdit = idParam !== undefined
  const id = isEdit ? Number(idParam) : undefined
  const validId = !isEdit || (Number.isFinite(id) && (id as number) > 0)

  if (!validId) {
    return (
      <>
        <PageHeader title="Produto" />
        <EmptyState title="Produto inválido" description="O endereço não aponta para um produto válido." />
      </>
    )
  }

  return isEdit ? <EditProductForm id={id as number} /> : <CreateProductForm />
}

/* ---------- criacao ---------- */

function CreateProductForm() {
  const router = useRouter()
  const categoriesQuery = useCategories()
  const createProduct = useCreateProduct()
  const requestDraftPhotoUploadUrl = useRequestDraftPhotoUploadUrl()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [idCategory, setIdCategory] = useState('')
  const [price, setPrice] = useState('')
  const [stock, setStock] = useState('0')
  const [photos, setPhotos] = useState<PhotoEditorItem[]>([])

  const validPrice = PRICE_PATTERN.test(price)
  const validStock = stock === '' || (/^\d+$/.test(stock) && Number(stock) >= 0)
  const canSubmit = name.trim().length > 0 && name.length <= 200 && idCategory !== '' && validPrice && validStock

  function addPhotoLocal(photoUrl: string) {
    setPhotos((prev) => [...prev, { photoUrl, position: prev.length }])
  }

  function removePhotoLocal(target: PhotoEditorItem) {
    setPhotos((prev) =>
      prev.filter((photo) => photo.photoUrl !== target.photoUrl).map((photo, index) => ({ ...photo, position: index })),
    )
  }

  function movePhotoLocal(target: PhotoEditorItem, direction: 'up' | 'down') {
    setPhotos((prev) => {
      const sorted = [...prev].sort((a, b) => a.position - b.position)
      const index = sorted.findIndex((photo) => photo.photoUrl === target.photoUrl)
      const swapWith = direction === 'up' ? index - 1 : index + 1
      if (swapWith < 0 || swapWith >= sorted.length) return prev
      const reordered = [...sorted]
      const [moved] = reordered.splice(index, 1)
      reordered.splice(swapWith, 0, moved)
      return reordered.map((photo, position) => ({ ...photo, position }))
    })
  }

  function submit() {
    if (!canSubmit || createProduct.isPending) return
    createProduct.mutate(
      {
        name: name.trim(),
        description: description.trim() || undefined,
        idCategory: Number(idCategory),
        price,
        stock: stock === '' ? undefined : Number(stock),
        photos: photos.length > 0 ? photos.map(({ photoUrl, position }) => ({ photoUrl, position })) : undefined,
      },
      { onSuccess: () => router.push('/store/products') },
    )
  }

  return (
    <>
      <PageHeader title="Novo produto" description="Produto novo entra sempre pela loja — não existe seleção de vendedor." />

      <div className="flex flex-col gap-6">
        <Card className="flex flex-col gap-4 p-4">
          <Field label="Nome" required error={name.length > 200 ? 'Máximo de 200 caracteres.' : undefined}>
            <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={220} />
          </Field>

          <Field label="Descrição" hint="Opcional">
            <Textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={3} />
          </Field>

          <Field label="Categoria" required>
            <Select value={idCategory} onChange={(event) => setIdCategory(event.target.value)}>
              <option value="">Selecione</option>
              {categoriesQuery.data?.content.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </Select>
          </Field>

          <div className="grid grid-cols-2 gap-4">
            <Field label="Preço" required hint="Ex.: 349,90 — fechamos em duas casas" error={price && !validPrice ? 'Use 2 casas decimais.' : undefined}>
              <Input
              value={price}
              onChange={(event) => setPrice(limparPreco(event.target.value))}
              onBlur={(event) => setPrice(formatarPreco(event.target.value))}
              inputMode="decimal"
            />
            </Field>

            <Field label="Estoque inicial" hint="Padrão 0" error={stock && !validStock ? 'Inteiro, maior ou igual a 0.' : undefined}>
              <Input value={stock} onChange={(event) => setStock(event.target.value)} inputMode="numeric" />
            </Field>
          </div>
        </Card>

        <Card className="p-4">
          <h2 className="mb-3 text-sm font-semibold text-ink">Fotos</h2>
          <PhotoEditor
            photos={photos}
            onAdd={addPhotoLocal}
            onRemove={removePhotoLocal}
            onMove={movePhotoLocal}
            onRequestUploadUrl={(file) =>
              requestDraftPhotoUploadUrl.mutateAsync({ fileName: file.name, contentType: file.type, sizeBytes: file.size })
            }
          />
        </Card>

        {createProduct.isError && (
          <p className="text-sm text-rose-600">{errorDescription(createProduct.error) ?? errorTitle(createProduct.error)}</p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => router.push('/store/products')} disabled={createProduct.isPending}>
            Cancelar
          </Button>
          <Button onClick={submit} loading={createProduct.isPending} disabled={!canSubmit}>
            Criar produto
          </Button>
        </div>
      </div>
    </>
  )
}

/* ---------- edicao ---------- */

function EditProductForm({ id }: { id: number }) {
  const router = useRouter()
  const categoriesQuery = useCategories()
  const productQuery = useManageProduct(id)
  const patchProduct = usePatchProduct(id)
  const updateStock = useUpdateStock(id)
  const deleteProduct = useDeleteProduct()
  const addPhoto = useAddPhoto(id)
  const deletePhoto = useDeletePhoto(id)
  const reorderPhotos = useReorderPhotos(id)
  const requestPhotoUploadUrl = useRequestPhotoUploadUrl(id)

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [idCategory, setIdCategory] = useState('')
  const [price, setPrice] = useState('')
  const [stockInput, setStockInput] = useState('')
  const [loadedFrom, setLoadedFrom] = useState<number | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  // Carrega o formulario uma vez por produto: se recarregar so' por causa da
  // invalidacao de cache (ex. apos ajustar estoque), nao pisa no que o owner digitou.
  useEffect(() => {
    if (!productQuery.data || loadedFrom === id) return
    setName(productQuery.data.name)
    setDescription(productQuery.data.description)
    setIdCategory(productQuery.data.category ? String(productQuery.data.category.id) : '')
    setPrice(productQuery.data.price)
    setLoadedFrom(id)
  }, [productQuery.data, id, loadedFrom])

  if (productQuery.isLoading) {
    return (
      <>
        <PageHeader title="Editar produto" />
        <div className="flex flex-col gap-3">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      </>
    )
  }

  if (productQuery.isError || !productQuery.data) {
    return (
      <>
        <PageHeader title="Editar produto" />
        <ErrorState
          title={errorTitle(productQuery.error)}
          description={errorDescription(productQuery.error)}
          onRetry={() => void productQuery.refetch()}
        />
      </>
    )
  }

  const product = productQuery.data
  const validPrice = PRICE_PATTERN.test(price)
  const canSubmit = name.trim().length > 0 && name.length <= 200 && idCategory !== '' && validPrice

  function submitPatch() {
    if (!canSubmit || patchProduct.isPending) return
    patchProduct.mutate({
      name: name.trim(),
      description: description.trim(),
      idCategory: Number(idCategory),
      price,
    })
  }

  function submitAbsoluteStock() {
    const value = Number(stockInput)
    if (!Number.isInteger(value) || value < 0 || updateStock.isPending) return
    updateStock.mutate({ stock: value }, { onSuccess: () => setStockInput('') })
  }

  function applyDelta(delta: number) {
    if (updateStock.isPending) return
    updateStock.mutate({ delta })
  }

  function submitDelete() {
    if (!confirmDelete) {
      setConfirmDelete(true)
      return
    }
    deleteProduct.mutate(id, { onSuccess: () => router.push('/store/products') })
  }

  function nextPosition() {
    return product.photos.length === 0 ? 0 : Math.max(...product.photos.map((photo) => photo.position)) + 1
  }

  function addPhotoRemote(photoUrl: string) {
    addPhoto.mutate({ photoUrl, position: nextPosition() })
  }

  function removePhotoRemote(target: PhotoEditorItem) {
    if (target.id === undefined) return
    deletePhoto.mutate(target.id)
  }

  function movePhotoRemote(target: PhotoEditorItem, direction: 'up' | 'down') {
    const sorted = [...product.photos].sort((a, b) => a.position - b.position)
    const index = sorted.findIndex((photo) => photo.id === target.id)
    const swapWith = direction === 'up' ? index - 1 : index + 1
    if (swapWith < 0 || swapWith >= sorted.length) return
    const reordered = [...sorted]
    const [moved] = reordered.splice(index, 1)
    reordered.splice(swapWith, 0, moved)
    reorderPhotos.mutate({ order: reordered.map((photo, position) => ({ id: photo.id, position })) })
  }

  return (
    <>
      <PageHeader
        title={product.name}
        description={`Preço atual ${money(product.price)} · atualizado em ${dateTime(product.updatedAt)}`}
      />

      <div className="flex flex-col gap-6">
        <Card className="flex flex-col gap-4 p-4">
          <Field label="Nome" required error={name.length > 200 ? 'Máximo de 200 caracteres.' : undefined}>
            <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={220} />
          </Field>

          <Field label="Descrição">
            <Textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={3} />
          </Field>

          <Field label="Categoria" required>
            <Select value={idCategory} onChange={(event) => setIdCategory(event.target.value)}>
              <option value="">Selecione</option>
              {categoriesQuery.data?.content.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Preço" required hint="Ex.: 349,90 — fechamos em duas casas" error={price && !validPrice ? 'Use 2 casas decimais.' : undefined}>
            <Input
              value={price}
              onChange={(event) => setPrice(limparPreco(event.target.value))}
              onBlur={(event) => setPrice(formatarPreco(event.target.value))}
              inputMode="decimal"
            />
          </Field>

          {patchProduct.isError && (
            <p className="text-sm text-rose-600">{errorDescription(patchProduct.error) ?? errorTitle(patchProduct.error)}</p>
          )}

          <div className="flex justify-end">
            <Button onClick={submitPatch} loading={patchProduct.isPending} disabled={!canSubmit}>
              Salvar alterações
            </Button>
          </div>
        </Card>

        <Card className="flex flex-col gap-4 p-4">
          <h2 className="text-sm font-semibold text-ink">Estoque</h2>
          <p className="text-sm text-muted">
            Estoque bruto: <span className="font-medium text-ink">{product.stock}</span> · Disponível para venda:{' '}
            <span className="font-medium text-ink">{product.available}</span>
          </p>

          <div className="flex flex-wrap items-center gap-2">
            <Button variant="secondary" size="sm" onClick={() => applyDelta(-1)} disabled={updateStock.isPending}>
              -1
            </Button>
            <Button variant="secondary" size="sm" onClick={() => applyDelta(1)} disabled={updateStock.isPending}>
              +1
            </Button>
            <Button variant="secondary" size="sm" onClick={() => applyDelta(-10)} disabled={updateStock.isPending}>
              -10
            </Button>
            <Button variant="secondary" size="sm" onClick={() => applyDelta(10)} disabled={updateStock.isPending}>
              +10
            </Button>
          </div>

          <Field label="Definir estoque absoluto" hint="Substitui o valor atual">
            <div className="flex gap-2">
              <Input value={stockInput} onChange={(event) => setStockInput(event.target.value)} inputMode="numeric" />
              <Button variant="secondary" onClick={submitAbsoluteStock} loading={updateStock.isPending}>
                Definir
              </Button>
            </div>
          </Field>

          {updateStock.isError && (
            <p className="text-sm text-rose-600">{errorDescription(updateStock.error) ?? errorTitle(updateStock.error)}</p>
          )}
        </Card>

        <Card className="p-4">
          <h2 className="mb-3 text-sm font-semibold text-ink">Fotos</h2>
          <PhotoEditor
            photos={product.photos}
            onAdd={addPhotoRemote}
            onRemove={removePhotoRemote}
            onMove={movePhotoRemote}
            onRequestUploadUrl={(file) =>
              requestPhotoUploadUrl.mutateAsync({ fileName: file.name, contentType: file.type, sizeBytes: file.size })
            }
            busy={addPhoto.isPending || deletePhoto.isPending || reorderPhotos.isPending}
          />
          {(addPhoto.isError || deletePhoto.isError || reorderPhotos.isError) && (
            <p className="mt-2 text-sm text-rose-600">
              {errorDescription(addPhoto.error ?? deletePhoto.error ?? reorderPhotos.error)}
            </p>
          )}
        </Card>

        <Card className="flex items-center justify-between gap-3 p-4">
          <div>
            <p className="text-sm font-medium text-ink">Remover produto</p>
            <p className="text-sm text-muted">Some da vitrine, mas continua visível em pedidos antigos.</p>
          </div>
          <Button variant="danger" onClick={submitDelete} loading={deleteProduct.isPending}>
            {confirmDelete ? 'Confirmar exclusão' : 'Excluir produto'}
          </Button>
        </Card>

        {deleteProduct.isError && (
          <p className="text-sm text-rose-600">{errorDescription(deleteProduct.error) ?? errorTitle(deleteProduct.error)}</p>
        )}
      </div>
    </>
  )
}
