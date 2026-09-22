import { ApiError } from '@/lib/api'

/** Titulo curto por status HTTP. Espelha catalog/errors.ts, mas com o caso 403 proprio
 * da loja: aqui 403 quase sempre significa "essa conta nao e' a dona", nao "sem permissao"
 * generico — e o texto nao pode vazar detalhe interno (regra do contrato). */
export function errorTitle(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Algo deu errado'
  switch (error.status) {
    case 0:
      return 'Sem conexão com o servidor'
    case 400:
      return 'Pedido inválido'
    case 403:
      return 'Acesso restrito à loja'
    case 404:
      return 'Não encontrado'
    case 409:
      return 'Não foi possível concluir'
    case 412:
      return 'Alguém mudou isso antes de você'
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

/** Descricao para o owner. 403 nunca repete o `detail` do backend — so' a frase padrao,
 * sem expor escopo ou papel interno. */
export function errorDescription(error: unknown): string | undefined {
  if (!(error instanceof ApiError)) return undefined
  if (error.status === 403) return 'Esta conta não é a dona da loja.'
  return error.message
}
