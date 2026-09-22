import { EmptyState, LinkButton, PageHeader } from '@/components/ui'

export default function NotFound() {
  return (
    <>
      <PageHeader title="Página não encontrada" />
      <EmptyState
        title="Este endereço não existe"
        description="O link pode estar errado ou a página pode ter saído do ar."
        action={<LinkButton to="/">Ir para a vitrine</LinkButton>}
      />
    </>
  )
}
