import { Button } from '../../../components/ui'
import { Modal } from '../../orders/components/Modal'
import { useEmptyCart } from '../queries'

/** Confirmacao antes de esvaziar: acao que apaga todas as linhas de uma vez. */
export function EmptyCartModal({ onClose }: { onClose: () => void }) {
  const emptyCart = useEmptyCart()

  return (
    <Modal title="Esvaziar carrinho" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted">Isso esvazia o carrinho por completo. Não dá para desfazer.</p>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={emptyCart.isPending}>
            Voltar
          </Button>
          <Button
            variant="danger"
            loading={emptyCart.isPending}
            onClick={() => emptyCart.mutate(undefined, { onSuccess: onClose })}
          >
            Esvaziar
          </Button>
        </div>
      </div>
    </Modal>
  )
}
