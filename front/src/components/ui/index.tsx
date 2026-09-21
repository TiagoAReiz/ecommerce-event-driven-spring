import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react'
import { Link } from 'react-router-dom'
import { cx } from '../../lib/format'

/* Kit visual da loja: branco e azul, um jeito so de desenhar cada coisa.
   Se uma tela precisar de um botao diferente, o botao entra aqui. */

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md'
  loading?: boolean
}

const BUTTON_BASE =
  'inline-flex items-center justify-center gap-2 rounded-[8px] font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60'

const BUTTON_VARIANT: Record<string, string> = {
  primary: 'bg-brand-700 text-white hover:bg-brand-800',
  secondary: 'border border-line bg-white text-ink hover:bg-brand-50',
  ghost: 'text-brand-700 hover:bg-brand-50',
  danger: 'bg-rose-600 text-white hover:bg-rose-700',
}

const BUTTON_SIZE: Record<string, string> = {
  sm: 'h-9 px-3 text-sm',
  md: 'h-11 px-5 text-sm',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading,
  children,
  className,
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={cx(BUTTON_BASE, BUTTON_VARIANT[variant], BUTTON_SIZE[size], className)}
      disabled={disabled || loading}
      {...rest}
    >
      {loading && <Spinner className="h-4 w-4" />}
      {children}
    </button>
  )
}

export function LinkButton({
  to,
  variant = 'primary',
  size = 'md',
  children,
  className,
}: {
  to: string
  variant?: 'primary' | 'secondary' | 'ghost'
  size?: 'sm' | 'md'
  children: ReactNode
  className?: string
}) {
  return (
    <Link to={to} className={cx(BUTTON_BASE, BUTTON_VARIANT[variant], BUTTON_SIZE[size], className)}>
      {children}
    </Link>
  )
}

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cx('rounded-[12px] border border-line bg-white', className)}>{children}</div>
}

export function Field({
  label,
  hint,
  error,
  children,
  required,
}: {
  label: string
  hint?: string
  error?: string
  children: ReactNode
  required?: boolean
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-ink">
        {label}
        {required && <span className="ml-0.5 text-rose-600">*</span>}
      </span>
      {children}
      {hint && !error && <span className="mt-1 block text-xs text-muted">{hint}</span>}
      {error && <span className="mt-1 block text-xs text-rose-600">{error}</span>}
    </label>
  )
}

const CONTROL =
  'w-full rounded-[8px] border border-line bg-white px-3 text-sm text-ink placeholder:text-slate-400 focus:border-brand-600 focus:outline-none'

export function Input({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cx(CONTROL, 'h-11', className)} {...rest} />
}

export function Select({ className, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={cx(CONTROL, 'h-11', className)} {...rest}>
      {children}
    </select>
  )
}

export function Textarea({ className, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cx(CONTROL, 'py-2', className)} {...rest} />
}

const BADGE_TONE: Record<string, string> = {
  neutral: 'bg-slate-100 text-slate-700',
  info: 'bg-brand-50 text-brand-700',
  success: 'bg-emerald-50 text-emerald-700',
  warning: 'bg-amber-50 text-amber-700',
  danger: 'bg-rose-50 text-rose-700',
}

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode
  tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger'
}) {
  return (
    <span className={cx('inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium', BADGE_TONE[tone])}>
      {children}
    </span>
  )
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      role="status"
      aria-label="Carregando"
      className={cx(
        'inline-block animate-spin rounded-full border-2 border-current border-t-transparent',
        className ?? 'h-5 w-5',
      )}
    />
  )
}

/** Esqueleto de carregamento: a tela nao pula quando o conteudo chega. */
export function Skeleton({ className }: { className?: string }) {
  return <div className={cx('animate-pulse rounded-[8px] bg-slate-100', className)} />
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-[12px] border border-dashed border-line px-6 py-12 text-center">
      <p className="text-base font-medium text-ink">{title}</p>
      {description && <p className="max-w-md text-sm text-muted">{description}</p>}
      {action}
    </div>
  )
}

/** Erro de carregamento com o motivo que o backend deu e um caminho de volta. */
export function ErrorState({
  title,
  description,
  onRetry,
}: {
  title: string
  description?: string
  onRetry?: () => void
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-[12px] border border-rose-200 bg-rose-50/50 px-6 py-10 text-center">
      <p className="text-base font-medium text-ink">{title}</p>
      {description && <p className="max-w-md text-sm text-muted">{description}</p>}
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Tentar de novo
        </Button>
      )}
    </div>
  )
}

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <nav className="flex items-center justify-center gap-3 py-6" aria-label="Paginação">
      <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Anterior
      </Button>
      <span className="text-sm text-muted">
        Página {page + 1} de {totalPages}
      </span>
      <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Próxima
      </Button>
    </nav>
  )
}

export function PageHeader({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3 py-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted">{description}</p>}
      </div>
      {action}
    </div>
  )
}
