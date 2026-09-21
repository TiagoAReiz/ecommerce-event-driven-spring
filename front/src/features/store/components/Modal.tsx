import type { ReactNode } from 'react'

/** Dialogo simples em overlay: a loja precisa de confirmacao antes de acoes
 * irreversiveis (cancelar, estornar, excluir) e o kit de UI compartilhado nao
 * tem um modal pronto. Mesma forma usada em orders/components/Modal.tsx, mas
 * uma copia local: a tarefa proibe editar fora de features/store. */
export function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/40 px-4 py-4 sm:items-center">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-[12px] border border-line bg-white p-5 shadow-lg"
      >
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold text-ink">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar"
            className="rounded-[8px] px-2 py-1 text-muted hover:bg-brand-50 hover:text-brand-700"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
