import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button, Card, EmptyState, ErrorState, Skeleton, cx } from '@/components/ui'
import { useCategories, useDeleteExpense, useExpenses } from '@/hooks/queries'
import { ExpenseForm } from './ExpenseForm'
import { ExpenseTable } from './ExpenseTable'
import type { Expense } from '@/types/schemas'

const PAGE_SIZE = 25

export function ExpensesPage() {
  // Filters live in the URL so a filtered view is linkable and survives a refresh.
  const [searchParams, setSearchParams] = useSearchParams()
  const [editing, setEditing] = useState<Expense | null>(null)

  const categories = useCategories()
  const remove = useDeleteExpense()

  const filters = {
    from: searchParams.get('from') ?? undefined,
    to: searchParams.get('to') ?? undefined,
    categoryId: searchParams.get('categoryId') ? Number(searchParams.get('categoryId')) : undefined,
    vendor: searchParams.get('vendor') ?? undefined,
    anomalyOnly: searchParams.get('anomalyOnly') === 'true',
    page: Number(searchParams.get('page') ?? '0'),
    size: PAGE_SIZE,
  }

  const expenses = useExpenses(filters)
  const hasFilters = Boolean(
    filters.from || filters.to || filters.categoryId || filters.vendor || filters.anomalyOnly,
  )

  function setFilter(key: string, value: string | undefined) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page') // a changed filter invalidates the current page
    setSearchParams(next, { replace: true })
  }

  async function handleDelete(expense: Expense) {
    if (!window.confirm(`Delete the ${expense.vendorName} expense of ₹${expense.amount}?`)) return
    await remove.mutateAsync(expense.id)
    if (editing?.id === expense.id) setEditing(null)
  }

  return (
    <div className="space-y-5">
      <Card title={editing ? `Edit expense · ${editing.vendorName}` : 'Add an expense'}>
        <ExpenseForm editing={editing} onDone={() => setEditing(null)} />
      </Card>

      <Card
        title="Expenses"
        action={
          <div className="flex items-center gap-2 text-xs text-ink-500">
            {expenses.isFetching && <span>updating…</span>}
            <span className="tnum">{expenses.data?.totalElements ?? 0} total</span>
          </div>
        }
      >
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <div>
            <label className="field-label" htmlFor="filter-from">
              From
            </label>
            <input
              id="filter-from"
              type="date"
              className="field-input w-40"
              value={filters.from ?? ''}
              onChange={(event) => setFilter('from', event.target.value || undefined)}
            />
          </div>
          <div>
            <label className="field-label" htmlFor="filter-to">
              To
            </label>
            <input
              id="filter-to"
              type="date"
              className="field-input w-40"
              value={filters.to ?? ''}
              onChange={(event) => setFilter('to', event.target.value || undefined)}
            />
          </div>
          <div>
            <label className="field-label" htmlFor="filter-category">
              Category
            </label>
            <select
              id="filter-category"
              className="field-input w-44"
              value={filters.categoryId ?? ''}
              onChange={(event) => setFilter('categoryId', event.target.value || undefined)}
            >
              <option value="">All categories</option>
              {categories.data?.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </div>
          <div className="min-w-[12rem] flex-1">
            <label className="field-label" htmlFor="filter-vendor">
              Vendor
            </label>
            <input
              id="filter-vendor"
              placeholder="Search vendor…"
              className="field-input"
              defaultValue={filters.vendor ?? ''}
              onKeyDown={(event) => {
                if (event.key === 'Enter') setFilter('vendor', event.currentTarget.value || undefined)
              }}
              onBlur={(event) => setFilter('vendor', event.target.value || undefined)}
            />
          </div>
          <button
            type="button"
            onClick={() => setFilter('anomalyOnly', filters.anomalyOnly ? undefined : 'true')}
            aria-pressed={filters.anomalyOnly}
            className={cx(
              'rounded-lg border px-3 py-2 text-sm font-medium transition-colors',
              filters.anomalyOnly
                ? 'border-amber-300 bg-amber-100 text-amber-900'
                : 'border-ink-200 bg-white text-ink-600 hover:bg-ink-50',
            )}
          >
            ⚠ Anomalies only
          </button>
          {hasFilters && (
            <Button variant="ghost" onClick={() => setSearchParams({}, { replace: true })}>
              Clear
            </Button>
          )}
        </div>

        {expenses.isPending ? (
          <div className="space-y-2">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-11 w-full" />
            ))}
          </div>
        ) : expenses.isError ? (
          <ErrorState error={expenses.error} onRetry={() => void expenses.refetch()} />
        ) : expenses.data.content.length === 0 ? (
          <EmptyState
            title={hasFilters ? 'No expenses match these filters' : 'No expenses yet'}
            description={
              hasFilters
                ? 'Try widening the date range or clearing the filters.'
                : 'Add one above, or upload a CSV to bring in a batch at once.'
            }
            action={
              hasFilters ? (
                <Button variant="secondary" onClick={() => setSearchParams({}, { replace: true })}>
                  Clear filters
                </Button>
              ) : undefined
            }
          />
        ) : (
          <>
            <ExpenseTable
              expenses={expenses.data.content}
              onEdit={setEditing}
              onDelete={handleDelete}
              deletingId={remove.isPending ? remove.variables : null}
            />
            <div className="mt-4 flex items-center justify-between text-sm text-ink-500">
              <span>
                Page {expenses.data.page + 1} of {Math.max(expenses.data.totalPages, 1)}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={expenses.data.page === 0}
                  onClick={() => setFilter('page', String(expenses.data.page - 1))}
                >
                  Previous
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={!expenses.data.hasNext}
                  onClick={() => setFilter('page', String(expenses.data.page + 1))}
                >
                  Next
                </Button>
              </div>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}
