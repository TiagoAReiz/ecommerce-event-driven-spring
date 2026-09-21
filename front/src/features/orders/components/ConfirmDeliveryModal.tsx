import { ApiError } from '../../../lib/api'
import { Button } from '../../../components/ui'
import { Modal } from './Modal'
import { useConfirmDelivery } from '../queries'

/**
 * A entrega e confirmada pelo COMPRADOR, nunca pela transportadora — por
 * isso pede uma confirmacao explicita antes de disparar o POST.
 */
export function ConfirmDeliveryModal({
  orderId,
  shipmentId,
  onClose,
}: {
  orderId: number
  shipmentId: number
  onClose: () => void
}) {
  const confirmDelivery = useConfirmDelivery(orderId, shipmentId)

  const errorMessage = confirmDelivery.error instanceof ApiError ? confirmDelivery.error.message : null

  return (
    <Modal title="Confirmar entrega" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink">
          Tem certeza que recebeu este pedido? Esta ação é irreversível e libera a avaliação do
          produto.
        </p>

        {errorMessage && <p className="text-sm text-ink">{errorMessage}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={confirmDelivery.isPending}>
            Cancelar
          </Button>
          <Button
            onClick={() => confirmDelivery.mutate(undefined, { onSuccess: onClose })}
            loading={confirmDelivery.isPending}
          >
            Confirmar
          </Button>
        </div>
      </div>
    </Modal>
  )
}
