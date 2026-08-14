import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { cx } from '@/components/ui'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { ExpensesPage } from '@/features/expenses/ExpensesPage'
import { ImportPage } from '@/features/import/ImportPage'

const NAV = [
  { to: '/', label: 'Expenses', end: true },
  { to: '/import', label: 'Import CSV', end: false },
  { to: '/dashboard', label: 'Dashboard', end: false },
]

export default function App() {
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-ink-200 bg-white/85 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-5 py-3">
          <span className="text-sm font-semibold tracking-tight text-ink-900">Expense Manager</span>
          <nav className="flex gap-1">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cx(
                    'rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
                    isActive ? 'bg-ink-900 text-white' : 'text-ink-600 hover:bg-ink-100',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-5 py-6">
        <Routes>
          <Route path="/" element={<ExpensesPage />} />
          <Route path="/import" element={<ImportPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}
