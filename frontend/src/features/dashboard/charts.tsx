import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { CATEGORY_FALLBACK_COLOR, formatMoney, formatMoneyCompact, formatMonth } from '@/lib/format'
import type { MonthlyByCategory, TopVendors } from '@/types/schemas'

const AXIS = { stroke: '#8591aa', fontSize: 12 }
const GRID = '#eceef2'

/**
 * Monthly totals per category as a stacked bar - one bar per month, one segment per category.
 * The backend zero-fills every series against a shared month axis, so the segments line up
 * without the chart having to reconcile ragged data.
 */
export function MonthlyCategoryChart({ data }: { data: MonthlyByCategory }) {
  const rows = data.months.map((month, index) => {
    const row: Record<string, string | number> = { month: formatMonth(month) }
    for (const series of data.series) {
      row[series.categoryName] = Number(series.totals[index] ?? '0')
    }
    return row
  })

  return (
    <ResponsiveContainer width="100%" height={320}>
      <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="month" tickLine={false} axisLine={{ stroke: GRID }} tick={AXIS} />
        <YAxis
          tickFormatter={(value: number) => formatMoneyCompact(value)}
          tickLine={false}
          axisLine={false}
          tick={AXIS}
          width={64}
        />
        <Tooltip
          cursor={{ fill: 'rgba(101,114,143,0.06)' }}
          formatter={(value, name) => [formatMoney(String(value)), String(name)]}
          contentStyle={{ borderRadius: 10, border: '1px solid #d5d9e2', fontSize: 12 }}
        />
        <Legend wrapperStyle={{ fontSize: 12, paddingTop: 8 }} />
        {data.series.map((series) => (
          <Bar
            key={series.categoryId}
            dataKey={series.categoryName}
            stackId="total"
            fill={series.colorHex ?? CATEGORY_FALLBACK_COLOR}
            radius={[0, 0, 0, 0]}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}

/**
 * Top vendors as horizontal bars - the labels are vendor names of varying length, which read
 * far better along the y-axis than rotated under a vertical bar.
 */
export function TopVendorsChart({ data }: { data: TopVendors }) {
  const rows = data.vendors.map((vendor) => ({
    vendor: vendor.vendorName,
    total: Number(vendor.totalAmount),
    count: vendor.expenseCount,
    category: vendor.topCategory,
  }))

  return (
    <ResponsiveContainer width="100%" height={Math.max(rows.length * 52, 160)}>
      <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 56, bottom: 4, left: 8 }}>
        <CartesianGrid stroke={GRID} horizontal={false} />
        <XAxis
          type="number"
          tickFormatter={(value: number) => formatMoneyCompact(value)}
          tickLine={false}
          axisLine={false}
          tick={AXIS}
        />
        <YAxis type="category" dataKey="vendor" tickLine={false} axisLine={false} tick={AXIS} width={120} />
        <Tooltip
          cursor={{ fill: 'rgba(101,114,143,0.06)' }}
          formatter={(value, _name, item) => {
            const row = item?.payload as { count?: number; category?: string } | undefined
            return [
              `${formatMoney(String(value))} · ${row?.count ?? 0} expense(s)`,
              row?.category ?? 'Total',
            ]
          }}
          contentStyle={{ borderRadius: 10, border: '1px solid #d5d9e2', fontSize: 12 }}
        />
        <Bar dataKey="total" radius={[0, 6, 6, 0]} barSize={22}>
          {rows.map((row) => (
            <Cell key={row.vendor} fill="#424a60" />
          ))}
          <LabelList
            dataKey="total"
            position="right"
            formatter={(value) => formatMoneyCompact(Number(value))}
            style={{ fill: '#505b76', fontSize: 11 }}
          />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}
