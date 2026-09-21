import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../../lib/api'
import { useAuth } from '../../../lib/auth'
import { Badge, Button, EmptyState, ErrorState, LinkButton, PageHeader, Skeleton } from '../../../components/ui'
import { money } from '../../../lib/format'
import { addToCart, fetchAvailability, fetchPhotos, fetchProduct } from '../api'
import { errorDescription, errorTitle } from '../errors'
import { PhotoGallery } from '../components/PhotoGallery'
import { RatingStars } from '../components/RatingStars'
import { ShippingCalculator } from '../components/ShippingCalculator'
import { ReviewsSection } from '../components/ReviewsSection'

const ITEM_QUANTITY_LIMIT = 99

export default function ProductDetailPage() {
  // useParams pode devolver undefined so na teoria (a rota exige :id); manter os
  // hooks incondicionais evita quebrar a ordem deles entre renders.
  const params = useParams<{ id: string }>()
  const id = params.id ?? ''

  const product = useQuery({
    queryKey: ['catalog', 'product', id],
    queryFn: () => fetchProduct(id),
    enabled: id !== '',
  })

  // Disponibilidade nunca e cacheada (contrato §4.2): sempre busca de novo.
  const availability = useQuery({
    queryKey: ['catalog', 'product', id, 'availability'],
    queryFn: () => fetchAvailability(id),
    staleTime: 0,
    enabled: id !== '',
  })

  const photos = useQuery({
    queryKey: ['catalog', 'product', id, 'photos'],
    queryFn: () => fetchPhotos(id),
    enabled: id !== '',
  })

  if (!id) {
    return (
      <>
        <PageHeader title="Produto" />
        <EmptyState title="Produto não encontrado" action={<LinkButton to="/products">Ver produtos</LinkButton>} />
      </>
    )
  }

  if (product.isLoading) {
    return (
      <div className="grid grid-cols-1 gap-8 py-6 md:grid-cols-2">
        <Skeleton className="aspect-square" />
        <div className="flex flex-col gap-3">
          <Skeleton className="h-8 w-3/4" />
          <Skeleton className="h-5 w-1/3" />
          <Skeleton className="h-10 w-1/4" />
        </div>
      </div>
    )
  }

  if (product.isError) {
    const notFound = product.error instanceof ApiError && (product.error.status === 404 || product.error.status === 400)
    if (notFound) {
      return (
        <>
          <PageHeader title="Produto" />
          <EmptyState
            title="Produto não encontrado"
            description="O link pode estar errado ou o produto pode ter saído do catálogo."
            action={<LinkButton to="/products">Ver produtos</LinkButton>}
          />
        </>
      )
    }
    return (
      <>
        <PageHeader title="Produto" />
        <ErrorState
          title={errorTitle(product.error)}
          description={errorDescription(product.error)}
          onRetry={() => void product.refetch()}
        />
      </>
    )
  }

  const data = product.data
  if (!data) return null

  return (
    <div className="py-6">
      <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
        <div>
          {photos.isLoading && <Skeleton className="aspect-square" />}
          {photos.isError && (
            <ErrorState
              title="Não foi possível carregar as fotos"
              description={errorDescription(photos.error)}
              onRetry={() => void photos.refetch()}
            />
          )}
          {photos.isSuccess && <PhotoGallery photos={photos.data} productName={data.name} />}
        </div>

        <div className="flex flex-col gap-4">
          <div>
            <Link to={`/products?categoryId=${data.category.id}`} className="text-sm text-brand-700 hover:underline">
              {data.category.name}
            </Link>
            <h1 className="mt-1 text-2xl font-semibold text-ink">{data.name}</h1>
            <RatingStars rating={data.rating} count={data.ratingCount} className="mt-2" />
          </div>

          <p className="text-3xl font-semibold text-ink">{money(data.price)}</p>

          <AvailabilityBadge
            isLoading={availability.isLoading}
            isError={availability.isError}
            available={availability.data?.available}
          />

          <p className="whitespace-pre-line text-sm text-muted">{data.description}</p>

          <AddToCartForm productId={data.id} maxAvailable={availability.data?.available} />

          <ShippingCalculator />
        </div>
      </div>

      <div className="mt-12">
        <ReviewsSection productId={id} />
      </div>
    </div>
  )
}

function AvailabilityBadge({
  isLoading,
  isError,
  available,
}: {
  isLoading: boolean
  isError: boolean
  available: number | undefined
}) {
  if (isLoading) return <Badge tone="neutral">Consultando estoque…</Badge>
  if (isError) return <Badge tone="neutral">Estoque indisponível no momento</Badge>
  if (available === undefined) return null
  if (available <= 0) return <Badge tone="danger">Sem estoque</Badge>
  if (available <= 5) return <Badge tone="warning">Restam {available} unidades</Badge>
  return <Badge tone="success">Em estoque</Badge>
}

function AddToCartForm({ productId, maxAvailable }: { productId: number; maxAvailable: number | undefined }) {
  const { status, login } = useAuth()
  const queryClient = useQueryClient()
  const [quantity, setQuantity] = useState(1)
  const [feedback, setFeedback] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (qty: number) => addToCart(productId, qty),
    onSuccess: () => {
      setFeedback('Adicionado ao carrinho.')
      void queryClient.invalidateQueries({ queryKey: ['cart'] })
    },
  })

  const soldOut = maxAvailable !== undefined && maxAvailable <= 0
  const max = Math.min(maxAvailable ?? ITEM_QUANTITY_LIMIT, ITEM_QUANTITY_LIMIT)

  function handleClick() {
    setFeedback(null)
    if (status !== 'authenticated') {
      login()
      return
    }
    mutation.mutate(quantity)
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <label htmlFor="quantidade" className="text-sm text-ink">
          Quantidade
        </label>
        <select
          id="quantidade"
          value={quantity}
          onChange={(event) => setQuantity(Number(event.target.value))}
          disabled={soldOut}
          className="h-11 rounded-[8px] border border-line bg-white px-3 text-sm text-ink focus:border-brand-600 focus:outline-none disabled:opacity-60"
        >
          {Array.from({ length: Math.max(max, 1) }, (_, index) => index + 1).map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
      </div>

      <Button onClick={handleClick} disabled={soldOut || mutation.isPending} loading={mutation.isPending}>
        {soldOut ? 'Produto esgotado' : 'Adicionar ao carrinho'}
      </Button>

      {feedback && <p className="text-sm text-emerald-700">{feedback}</p>}
      {mutation.isError && <p className="text-sm text-rose-600">{addToCartErrorMessage(mutation.error)}</p>}
    </div>
  )
}

function addToCartErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 404) return 'Este produto não está mais disponível.'
    if (error.status === 409) return 'Quantidade acima do disponível em estoque.'
    if (error.status === 413) return 'Seu carrinho já tem o máximo de itens diferentes (50).'
    if (error.status === 422) return 'Quantidade acima do limite por item (99).'
    if (error.status === 504) return 'O estoque demorou para responder. Tente de novo.'
    if (error.status === 0) return 'Sem conexão com o servidor.'
  }
  return 'Não foi possível adicionar ao carrinho agora. Tente novamente.'
}
