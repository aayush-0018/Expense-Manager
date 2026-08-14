import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from './App'
import { renderWithProviders } from '@/test/utils'

/**
 * A wiring smoke test: every route must mount without throwing. It catches the class of
 * mistake unit tests miss entirely - a bad import, a provider that is not in place, a hook
 * called outside its context.
 */
function stubEmptyApi() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      const body = url.includes('/categories')
        ? []
        : url.includes('/expenses/import/format')
          ? {
              templateHeader: 'date,amount,vendor,description',
              acceptedDateFormats: ['yyyy-MM-dd'],
              requiredColumns: ['date', 'amount', 'vendor'],
              optionalColumns: ['description', 'category'],
              columnAliases: {},
              notes: [],
            }
          : url.includes('/expenses')
            ? { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, hasNext: false }
            : url.includes('/summary')
              ? {
                  month: '2026-08',
                  totalAmount: '0.00',
                  expenseCount: 0,
                  anomalyCount: 0,
                  topCategoryName: null,
                  topCategoryAmount: null,
                }
              : url.includes('/monthly-by-category')
                ? { months: [], series: [] }
                : url.includes('/top-vendors')
                  ? { vendors: [] }
                  : { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false }

      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }),
  )
}

describe('App', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('renders the expenses route with an empty state', async () => {
    stubEmptyApi()
    renderWithProviders(<App />)

    expect(screen.getByRole('link', { name: /expenses/i })).toBeInTheDocument()
    expect(await screen.findByText(/no expenses yet/i)).toBeInTheDocument()
  })

  it('navigates to the import route', async () => {
    stubEmptyApi()
    renderWithProviders(<App />)

    await userEvent.click(screen.getByRole('link', { name: /import csv/i }))
    expect(await screen.findByText(/drop a \.csv file here/i)).toBeInTheDocument()
    expect(screen.getByText(/expected format/i)).toBeInTheDocument()
  })

  it('navigates to the dashboard route and shows its tiles', async () => {
    stubEmptyApi()
    renderWithProviders(<App />)

    await userEvent.click(screen.getByRole('link', { name: /dashboard/i }))
    expect(await screen.findByText(/total spend/i)).toBeInTheDocument()
    expect(screen.getByText(/top category/i)).toBeInTheDocument()
    expect(screen.getByText(/no anomalies detected/i)).toBeInTheDocument()
  })

  it('redirects an unknown route back to the expense list', async () => {
    stubEmptyApi()
    renderWithProviders(<App />, { route: '/nowhere' })

    expect(await screen.findByText(/no expenses yet/i)).toBeInTheDocument()
  })
})
