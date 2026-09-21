import type { ReactNode } from 'react'

/** Aviso de erro no mesmo tom em toda a area: sem cor fora dos tokens do projeto. */
export function ErrorNote({ children }: { children: ReactNode }) {
  return <div className="rounded-[12px] bg-brand-50 p-3 text-sm text-ink">{children}</div>
}
