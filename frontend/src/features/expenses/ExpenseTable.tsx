import { AnomalyBadge, Button, CategoryChip, cx } from '@/components/ui'
import { formatDate, formatMoney } from '@/lib/format'
import type { Expense } from '@/types/schemas'

/**
 * Anomalies are made visually distinct on three independent channels - a tinted row, a left
 * rule, and a labelled badge - so the flag survives greyscale printing, colour blindness and
 * a screen reader.
 */
export function ExpenseTable({
  expenses,
  onEdit,
  onDelete,
  deletingId,
}: {
  expenses: Expense[]
  onEdit: (expense: Expense) => void
  onDelete: (expense: Expense) => void
  deletingId?: number | null
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[52rem] border-collapse text-sm">
        <thead>
          <tr className="border-b border-ink-200 text-left text-xs uppercase tracking-wide text-ink-500">
            <th className="px-3 py-2.5 font-medium">Date</th>
            <th className="px-3 py-2.5 font-medium">Vendor</th>
            <th className="px-3 py-2.5 font-medium">Category</th>
            <th className="px-3 py-2.5 font-medium">Description</th>
            <th className="px-3 py-2.5 text-right font-medium">Amount</th>
            <th className="px-3 py-2.5 text-right font-medium">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {expenses.map((expense) => (
            <tr
              key={expense.id}
              aria-label={expense.isAnomaly ? 'Anomalous expense' : undefined}
              className={cx(
                'border-b border-ink-100 last:border-0',
                expense.isAnomaly ? 'bg-amber-50/70' : 'hover:bg-ink-50',
              )}
            >
              <td
                className={cx(
                  'whitespace-nowrap px-3 py-3 text-ink-600',
                  expense.isAnomaly && 'border-l-4 border-l-amber-400 pl-2',
                )}
              >
                {formatDate(expense.date)}
              </td>
              <td className="px-3 py-3">
                <div className="font-medium text-ink-900">{expense.vendorName}</div>
                {expense.categorizationSource === 'MANUAL_OVERRIDE' && (
                  <div className="text-xs text-ink-400">category set manually</div>
                )}
                {expense.categorizationSource === 'DEFAULT' && (
                  <div className="text-xs text-ink-400">no vendor rule matched</div>
                )}
              </td>
              <td className="px-3 py-3">
                <CategoryChip name={expense.category.name} colorHex={expense.category.colorHex} />
              </td>
              <td className="max-w-xs truncate px-3 py-3 text-ink-600" title={expense.description ?? undefined}>
                {expense.description ?? <span className="text-ink-300">—</span>}
              </td>
              <td className="whitespace-nowrap px-3 py-3 text-right">
                <div className="flex items-center justify-end gap-2">
                  {expense.isAnomaly && (
                    <AnomalyBadge
                      label="anomaly"
                      title={`${formatMoney(expense.amount)} is more than 3× the ${expense.category.name} average`}
                    />
                  )}
                  <span className="tnum font-semibold text-ink-900">{formatMoney(expense.amount)}</span>
                </div>
              </td>
              <td className="whitespace-nowrap px-3 py-3 text-right">
                <div className="flex justify-end gap-1">
                  <Button variant="ghost" size="sm" onClick={() => onEdit(expense)}>
                    Edit
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    disabled={deletingId === expense.id}
                    onClick={() => onDelete(expense)}
                  >
                    {deletingId === expense.id ? '…' : 'Delete'}
                  </Button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
