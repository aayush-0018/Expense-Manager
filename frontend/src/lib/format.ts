/**
 * Display helpers. Amounts arrive as decimal strings and are only ever *formatted* here -
 * they are never converted to a number and added up on the client.
 */

const currency = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const compact = new Intl.NumberFormat('en-IN', {
  notation: 'compact',
  maximumFractionDigits: 1,
})

/** Formats a decimal string as currency. Falls back to the raw string if it is not numeric. */
export function formatMoney(amount: string | null | undefined): string {
  if (amount == null) return '—'
  const value = Number(amount)
  return Number.isFinite(value) ? currency.format(value) : amount
}

/** Short form for chart axes, where full currency strings would collide. */
export function formatMoneyCompact(amount: string | number | null | undefined): string {
  if (amount == null) return '—'
  const value = typeof amount === 'number' ? amount : Number(amount)
  return Number.isFinite(value) ? `₹${compact.format(value)}` : String(amount)
}

export function formatDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  return Number.isNaN(date.getTime())
    ? iso
    : date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
}

/** "2026-08" -> "Aug 2026" */
export function formatMonth(yearMonth: string): string {
  const [year, month] = yearMonth.split('-')
  const date = new Date(Number(year), Number(month) - 1, 1)
  return Number.isNaN(date.getTime())
    ? yearMonth
    : date.toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })
}

export function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export function todayIso(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

/** Shifts a "yyyy-MM" string by a number of months. */
export function shiftMonth(yearMonth: string, delta: number): string {
  const [year, month] = yearMonth.split('-').map(Number)
  const date = new Date(year, month - 1 + delta, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

/** "4.20" -> "4.2x", so the badge stays short. */
export function formatMultiple(timesAverage: string): string {
  const value = Number(timesAverage)
  if (!Number.isFinite(value)) return `${timesAverage}×`
  return `${value.toFixed(1)}×`
}

export const CATEGORY_FALLBACK_COLOR = '#8D99AE'
