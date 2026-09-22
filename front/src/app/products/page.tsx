import ProductListPage, { readParams } from '@/features/catalog/pages/ProductListPage'
import { fetchProducts } from '@/features/catalog/api'

// Filtro/pagina vem da querystring: e Promise no Next 16, entao precisa await antes
// de ler qualquer chave.
type SearchParams = Promise<Record<string, string | string[] | undefined>>

/** Next entrega a querystring como objeto plano; readParams espera um URLSearchParams
 * (o mesmo leitor usado no cliente), entao so converte o formato aqui. */
function toURLSearchParams(searchParams: Record<string, string | string[] | undefined>): URLSearchParams {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(searchParams)) {
    if (value === undefined) continue
    if (Array.isArray(value)) {
      for (const item of value) params.append(key, item)
    } else {
      params.set(key, value)
    }
  }
  return params
}

export default async function Page({ searchParams }: { searchParams: SearchParams }) {
  const resolved = await searchParams
  const params = readParams(toURLSearchParams(resolved))

  // Gateway fora nao pode derrubar a listagem: sem os dados, a propria tela mostra o
  // estado de erro e tenta de novo no cliente.
  const initialData = await fetchProducts(params).catch(() => undefined)

  return <ProductListPage initialData={initialData} />
}
