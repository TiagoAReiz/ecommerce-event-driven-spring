import HomePage from '@/features/catalog/pages/HomePage'
import { fetchCategories, fetchProducts } from '@/features/catalog/api'

// A vitrine e a pagina que precisa aparecer em busca: sai renderizada do servidor,
// com o conteudo ja no HTML. As rotas com sessao continuam no cliente, porque o
// token vive no navegador.
//
// Por requisicao, e nao gerada no build: o catalogo nasce vazio e e cadastrado com
// a loja no ar. Uma pagina congelada no build mostraria a vitrine vazia para quem
// chegasse primeiro, e preco e estoque mudam o tempo todo.
export const dynamic = 'force-dynamic'

export default async function Page() {
  // Gateway fora nao pode derrubar a home: sem os dados, a propria tela mostra o
  // estado de erro e tenta de novo no cliente.
  const [categories, highlights] = await Promise.all([
    fetchCategories(false).catch(() => undefined),
    fetchProducts({ sort: 'rating,desc', size: 8, page: 0 }).catch(() => undefined),
  ])

  return <HomePage initialCategories={categories} initialHighlights={highlights} />
}
