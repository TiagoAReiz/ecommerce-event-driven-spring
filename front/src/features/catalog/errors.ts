import { ApiError } from '@/lib/api'

/** Titulo curto por status HTTP, para as telas do catalogo nao repetirem o mesmo switch.
 * O detalhe (motivo exato) sempre vem do backend via `error.message`. */
export function errorTitle(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Algo deu errado'
  switch (error.status) {
    case 0:
      return 'Sem conexão com o servidor'
    case 400:
      return 'Pedido inválido'
    case 404:
      return 'Não encontrado'
    case 422:
      return 'Não foi possível processar'
    case 429:
      return 'Muitas tentativas'
    case 504:
      return 'O servidor demorou para responder'
    default:
      return error.status >= 500 ? 'Erro no servidor' : 'Algo deu errado'
  }
}

export function errorDescription(error: unknown): string | undefined {
  if (error instanceof ApiError) return error.message
  return undefined
}
