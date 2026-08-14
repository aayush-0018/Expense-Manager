import type { ReactElement, ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { render } from '@testing-library/react'
import type { Expense } from '@/types/schemas'

export function renderWithProviders(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }

  return { queryClient, ...render(ui, { wrapper: Wrapper }) }
}

export function makeExpense(overrides: Partial<Expense> = {}): Expense {
  return {
    id: 1,
    date: '2026-08-01',
    amount: '450.00',
    vendorName: 'Swiggy',
    description: 'Team lunch',
    category: { id: 1, name: 'Food', colorHex: '#E76F51', isDefault: false },
    categorizationSource: 'RULE',
    isAnomaly: false,
    anomalyReason: null,
    importBatchId: null,
    createdAt: '2026-08-01T10:12:03Z',
    ...overrides,
  }
}
