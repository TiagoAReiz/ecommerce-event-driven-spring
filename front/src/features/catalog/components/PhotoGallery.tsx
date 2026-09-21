import { useState } from 'react'
import { cx } from '../../../lib/format'
import type { ProductPhoto } from '../types'

/** Foto principal + miniaturas. Sem foto nenhuma, cai no fallback: nunca fica com
 * espaco vazio quebrando o layout. */
export function PhotoGallery({ photos, productName }: { photos: ProductPhoto[]; productName: string }) {
  const sorted = [...photos].sort((a, b) => a.position - b.position)
  const [selected, setSelected] = useState(0)
  const current = sorted[selected] ?? sorted[0]

  if (sorted.length === 0) {
    return (
      <div className="flex aspect-square w-full items-center justify-center rounded-[12px] border border-line bg-brand-50 text-sm text-muted">
        Sem foto
      </div>
    )
  }

  return (
    <div>
      <div className="aspect-square w-full overflow-hidden rounded-[12px] border border-line bg-brand-50">
        <img src={current.photoUrl} alt={productName} className="h-full w-full object-cover" />
      </div>
      {sorted.length > 1 && (
        <div className="mt-2 flex gap-2 overflow-x-auto pb-1">
          {sorted.map((photo, index) => (
            <button
              key={photo.id}
              type="button"
              onClick={() => setSelected(index)}
              aria-label={`Foto ${index + 1} de ${sorted.length}`}
              aria-current={index === selected}
              className={cx(
                'h-16 w-16 shrink-0 overflow-hidden rounded-[8px] border-2',
                index === selected ? 'border-brand-600' : 'border-line',
              )}
            >
              <img src={photo.photoUrl} alt="" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
