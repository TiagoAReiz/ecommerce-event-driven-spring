import { Badge } from '../../../components/ui'
import type { CartIssueCode } from '../types'

/** Um selo por issue do item, na mesma linguagem visual do resto do app. */
const ISSUE_LABEL: Record<CartIssueCode, string> = {
  PRODUCT_UNAVAILABLE: 'Produto indisponível',
  INSUFFICIENT_STOCK: 'Estoque insuficiente',
  PRICE_CHANGED: 'Preço mudou',
  HYDRATION_TIMEOUT: 'Carregando dados do produto',
}

const ISSUE_TONE: Record<CartIssueCode, 'warning' | 'danger' | 'neutral'> = {
  PRODUCT_UNAVAILABLE: 'danger',
  INSUFFICIENT_STOCK: 'warning',
  PRICE_CHANGED: 'warning',
  HYDRATION_TIMEOUT: 'neutral',
}

export function CartIssueBadges({ issues }: { issues: CartIssueCode[] }) {
  if (issues.length === 0) return null
  return (
    <div className="flex flex-wrap gap-1.5">
      {issues.map((issue) => (
        <Badge key={issue} tone={ISSUE_TONE[issue]}>
          {ISSUE_LABEL[issue]}
        </Badge>
      ))}
    </div>
  )
}
