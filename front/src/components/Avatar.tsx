'use client'

import { useEffect, useState } from 'react'
import { cx } from '@/lib/format'

/**
 * Foto do usuario, com a inicial do nome como reserva.
 *
 * <p>Imagem de fora pode simplesmente nao carregar -- o CDN do Google, por
 * exemplo, recusa a foto pedida a partir de outro dominio. Sem a reserva, a tela
 * fica com um quadrado vazio, que parece defeito.
 */
export function Avatar({
  src,
  name,
  size = 28,
  className,
}: {
  src: string | null | undefined
  name: string
  /** Lado do circulo em pixels. */
  size?: number
  className?: string
}) {
  const [quebrou, setQuebrou] = useState(false)

  // Foto nova depois de uma troca merece uma tentativa nova.
  useEffect(() => setQuebrou(false), [src])

  const estilo = { width: size, height: size }
  const inicial = name?.trim()?.slice(0, 1)?.toUpperCase() || '?'

  if (!src || quebrou) {
    return (
      <span
        aria-hidden
        style={estilo}
        className={cx(
          'flex shrink-0 items-center justify-center rounded-full bg-brand-100 font-medium text-brand-700',
          size >= 48 ? 'text-xl' : 'text-xs',
          className,
        )}
      >
        {inicial}
      </span>
    )
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt=""
      style={estilo}
      onError={() => setQuebrou(true)}
      // Nao manda de onde veio: alguns servidores recusam a imagem por causa disso.
      referrerPolicy="no-referrer"
      className={cx('shrink-0 rounded-full object-cover', className)}
    />
  )
}
