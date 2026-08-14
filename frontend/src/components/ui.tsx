import type { ReactNode } from 'react'
import { CATEGORY_FALLBACK_COLOR } from '@/lib/format'

export function cx(...values: (string | false | null | undefined)[]): string {
  return values.filter(Boolean).join(' ')
}

export function Card({
  title,
  action,
  children,
  className,
}: {
  title?: ReactNode
  action?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={cx('rounded-xl border border-ink-200 bg-white shadow-sm', className)}>
      {(title || action) && (
        <header className="flex items-center justify-between gap-4 border-b border-ink-100 px-5 py-3.5">
          {typeof title === 'string' ? <h2 className="text-sm font-semibold text-ink-800">{title}</h2> : title}
          {action}
        </header>
      )}
      <div className="p-5">{children}</div>
    </section>
  )
}

export function Button({
  variant = 'primary',
  size = 'md',
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md'
}) {
  const variants = {
    primary: 'bg-ink-900 text-white hover:bg-ink-800 disabled:bg-ink-300',
    secondary: 'border border-ink-200 bg-white text-ink-700 hover:bg-ink-50 disabled:text-ink-300',
    ghost: 'text-ink-600 hover:bg-ink-100 disabled:text-ink-300',
    danger: 'border border-rose-200 bg-white text-rose-600 hover:bg-rose-50 disabled:text-rose-300',
  }
  const sizes = { sm: 'px-2.5 py-1.5 text-xs', md: 'px-4 py-2 text-sm' }
  return (
    <button
      {...props}
      className={cx(
        'inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition-colors',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-ink-500/40 disabled:cursor-not-allowed',
        variants[variant],
        sizes[size],
        className,
      )}
    />
  )
}

export function CategoryChip({ name, colorHex }: { name: string; colorHex: string | null }) {
  const color = colorHex ?? CATEGORY_FALLBACK_COLOR
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full bg-ink-100 px-2.5 py-0.5 text-xs font-medium text-ink-700">
      <span aria-hidden className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: color }} />
      {name}
    </span>
  )
}

/**
 * The anomaly marker. Colour is never the only signal - the badge carries an icon, the
 * multiple as text, and a title, so it survives greyscale and screen readers alike.
 */
export function AnomalyBadge({ label, title }: { label: string; title: string }) {
  return (
    <span
      title={title}
      aria-label={title}
      className="inline-flex items-center gap-1 whitespace-nowrap rounded-md border border-amber-300 bg-amber-50 px-1.5 py-0.5 text-xs font-semibold text-amber-800"
    >
      <span aria-hidden>⚠</span>
      {label}
    </span>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cx('animate-pulse rounded-md bg-ink-100', className)} />
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-12 text-center">
      <p className="text-sm font-semibold text-ink-800">{title}</p>
      <p className="max-w-sm text-sm text-ink-500">{description}</p>
      {action && <div className="mt-3">{action}</div>}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : 'Something went wrong'
  return (
    <div
      role="alert"
      className="flex flex-col items-center justify-center gap-3 rounded-lg border border-rose-200 bg-rose-50 px-6 py-8 text-center"
    >
      <p className="text-sm font-semibold text-rose-800">Could not load this</p>
      <p className="max-w-md text-sm text-rose-700">{message}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

export function StatTile({
  label,
  value,
  hint,
  tone = 'default',
  onClick,
  loading,
}: {
  label: string
  value: string
  hint?: string
  tone?: 'default' | 'warning'
  onClick?: () => void
  loading?: boolean
}) {
  const body = (
    <>
      <p className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</p>
      {loading ? (
        <Skeleton className="mt-2 h-7 w-24" />
      ) : (
        <p
          className={cx(
            'tnum mt-1 text-2xl font-semibold',
            tone === 'warning' ? 'text-amber-700' : 'text-ink-900',
          )}
        >
          {value}
        </p>
      )}
      {hint && <p className="mt-1 text-xs text-ink-500">{hint}</p>}
    </>
  )

  const base = cx(
    'rounded-xl border bg-white px-5 py-4 text-left shadow-sm',
    tone === 'warning' ? 'border-amber-200' : 'border-ink-200',
  )

  return onClick ? (
    <button type="button" onClick={onClick} className={cx(base, 'transition-colors hover:bg-ink-50')}>
      {body}
    </button>
  ) : (
    <div className={base}>{body}</div>
  )
}
