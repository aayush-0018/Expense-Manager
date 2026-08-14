import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AnomalyBadge, Button, Card, CategoryChip, EmptyState, ErrorState, Skeleton, StatTile } from '@/components/ui'
import { useAnomalies, useDashboardSummary, useMonthlyByCategory, useTopVendors } from '@/hooks/queries'
import { currentMonth, formatDate, formatMoney, formatMonth, formatMultiple, shiftMonth } from '@/lib/format'
import { MonthlyCategoryChart, TopVendorsChart } from './charts'
import type { AnomalyItem } from '@/types/schemas'

const TREND_MONTHS = 6

export function DashboardPage() {
  const navigate = useNavigate()
  const [month, setMonth] = useState<string>(currentMonth())

  const summary = useDashboardSummary(month)
  const monthly = useMonthlyByCategory(shiftMonth(month, -(TREND_MONTHS - 1)), month)
  const vendors = useTopVendors(month, 5)
  const anomalies = useAnomalies(0, 10)

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-ink-900">Dashboard</h1>
          <p className="text-sm text-ink-500">Totals for {formatMonth(month)}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" onClick={() => setMonth(shiftMonth(month, -1))}>
            ← Previous
          </Button>
          <input
            type="month"
            aria-label="Month"
            className="field-input w-40"
            value={month}
            max={currentMonth()}
            onChange={(event) => event.target.value && setMonth(event.target.value)}
          />
          <Button
            variant="secondary"
            size="sm"
            disabled={month >= currentMonth()}
            onClick={() => setMonth(shiftMonth(month, 1))}
          >
            Next →
          </Button>
        </div>
      </div>

      {summary.isError ? (
        <ErrorState error={summary.error} onRetry={() => void summary.refetch()} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="Total spend"
            value={formatMoney(summary.data?.totalAmount)}
            hint={formatMonth(month)}
            loading={summary.isPending}
          />
          <StatTile
            label="Expenses"
            value={String(summary.data?.expenseCount ?? 0)}
            hint="recorded this month"
            loading={summary.isPending}
          />
          <StatTile
            label="Anomalies"
            value={String(summary.data?.anomalyCount ?? 0)}
            hint="all time · click to review"
            tone="warning"
            loading={summary.isPending}
            onClick={() => navigate('/?anomalyOnly=true')}
          />
          <StatTile
            label="Top category"
            value={summary.data?.topCategoryName ?? '—'}
            hint={summary.data?.topCategoryAmount ? formatMoney(summary.data.topCategoryAmount) : 'no spend yet'}
            loading={summary.isPending}
          />
        </div>
      )}

      <Card title={`Monthly totals per category · last ${TREND_MONTHS} months`}>
        {monthly.isPending ? (
          <Skeleton className="h-72 w-full" />
        ) : monthly.isError ? (
          <ErrorState error={monthly.error} onRetry={() => void monthly.refetch()} />
        ) : monthly.data.series.length === 0 ? (
          <EmptyState title="Nothing to chart yet" description="Add expenses or upload a CSV to see monthly totals." />
        ) : (
          <MonthlyCategoryChart data={monthly.data} />
        )}
      </Card>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card title="Top 5 vendors by spend">
          {vendors.isPending ? (
            <Skeleton className="h-56 w-full" />
          ) : vendors.isError ? (
            <ErrorState error={vendors.error} onRetry={() => void vendors.refetch()} />
          ) : vendors.data.vendors.length === 0 ? (
            <EmptyState title="No vendors this month" description="Pick another month or add some expenses." />
          ) : (
            <TopVendorsChart data={vendors.data} />
          )}
        </Card>

        <Card
          title="Anomalies"
          action={
            <span className="text-xs text-ink-500">
              more than 3× the category average
            </span>
          }
        >
          {anomalies.isPending ? (
            <Skeleton className="h-56 w-full" />
          ) : anomalies.isError ? (
            <ErrorState error={anomalies.error} onRetry={() => void anomalies.refetch()} />
          ) : anomalies.data.content.length === 0 ? (
            <EmptyState
              title="No anomalies detected"
              description="Nothing recorded is more than 3× the average for its category."
            />
          ) : (
            <ul className="divide-y divide-ink-100">
              {anomalies.data.content.map((item) => (
                <AnomalyRow key={item.expense.id} item={item} />
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}

function AnomalyRow({ item }: { item: AnomalyItem }) {
  const { expense } = item
  return (
    <li className="flex items-start justify-between gap-4 border-l-4 border-l-amber-400 bg-amber-50/40 py-3 pl-3 pr-1 first:rounded-t-md last:rounded-b-md">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className="truncate font-medium text-ink-900">{expense.vendorName}</span>
          <CategoryChip name={expense.category.name} colorHex={expense.category.colorHex} />
        </div>
        <p className="mt-0.5 text-xs text-ink-500">
          {formatDate(expense.date)}
          {expense.description ? ` · ${expense.description}` : ''}
        </p>
        <p className="mt-1 text-xs text-ink-600">
          {expense.category.name} average is{' '}
          <span className="tnum font-medium">{formatMoney(item.categoryAverage)}</span> — flagged above{' '}
          <span className="tnum font-medium">{formatMoney(item.threshold)}</span>
        </p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1">
        <span className="tnum font-semibold text-ink-900">{formatMoney(expense.amount)}</span>
        <AnomalyBadge
          label={formatMultiple(item.timesAverage)}
          title={`${formatMoney(expense.amount)} is ${formatMultiple(item.timesAverage)} the ${expense.category.name} average of ${formatMoney(item.categoryAverage)}`}
        />
      </div>
    </li>
  )
}
