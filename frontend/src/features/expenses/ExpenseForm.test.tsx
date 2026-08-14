import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ExpenseForm } from './ExpenseForm'
import { renderWithProviders } from '@/test/utils'

const CATEGORIES = [
  { id: 1, name: 'Food', colorHex: '#E76F51', isDefault: false },
  { id: 3, name: 'Travel', colorHex: '#264653', isDefault: false },
]

/** Minimal fetch stub - enough to serve categories and capture the created expense. */
function stubFetch(handler?: (url: string, init?: RequestInit) => Response | Promise<Response>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.includes('/categories')) {
      return new Response(JSON.stringify(CATEGORIES), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    if (handler) return handler(url, init)
    return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('ExpenseForm', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date('2026-08-13T09:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('blocks submission and names the offending fields', async () => {
    const fetchMock = stubFetch()
    const user = userEvent.setup()
    renderWithProviders(<ExpenseForm />)

    await user.clear(screen.getByLabelText(/amount/i))
    await user.click(screen.getByRole('button', { name: /add expense/i }))

    expect(await screen.findByText(/amount is required/i)).toBeInTheDocument()
    expect(await screen.findByText(/vendor name is required/i)).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/expenses'))).toHaveLength(0)
  })

  it('rejects an amount with more than two decimal places before contacting the server', async () => {
    stubFetch()
    const user = userEvent.setup()
    renderWithProviders(<ExpenseForm />)

    await user.type(screen.getByLabelText(/amount/i), '10.005')
    await user.type(screen.getByLabelText(/vendor/i), 'Swiggy')
    await user.click(screen.getByRole('button', { name: /add expense/i }))

    expect(await screen.findByText(/at most 2 decimal places/i)).toBeInTheDocument()
  })

  it('sends a valid expense with categoryId null when the category is left automatic', async () => {
    let captured: unknown = null
    const fetchMock = stubFetch((url, init) => {
      if (url.includes('/expenses') && init?.method === 'POST') {
        captured = JSON.parse(String(init.body))
        return new Response(
          JSON.stringify({
            id: 1,
            date: '2026-08-10',
            amount: '450.00',
            vendorName: 'Swiggy',
            description: 'Lunch',
            category: CATEGORIES[0],
            categorizationSource: 'RULE',
            isAnomaly: false,
            anomalyReason: null,
            importBatchId: null,
            createdAt: '2026-08-10T00:00:00Z',
          }),
          { status: 201, headers: { 'Content-Type': 'application/json' } },
        )
      }
      return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })

    const user = userEvent.setup()
    renderWithProviders(<ExpenseForm />)

    await user.type(screen.getByLabelText(/amount/i), '450.00')
    await user.type(screen.getByLabelText(/vendor/i), 'Swiggy')
    await user.type(screen.getByLabelText(/description/i), 'Lunch')
    await user.click(screen.getByRole('button', { name: /add expense/i }))

    await waitFor(() => expect(captured).not.toBeNull())
    expect(captured).toMatchObject({
      amount: '450.00',
      vendorName: 'Swiggy',
      description: 'Lunch',
      categoryId: null,
    })
    expect(fetchMock).toHaveBeenCalled()
  })

  it('maps a server-side field error back onto the field that caused it', async () => {
    stubFetch((url, init) => {
      if (url.includes('/expenses') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            status: 400,
            error: 'VALIDATION_FAILED',
            message: 'Request validation failed',
            fieldErrors: [{ field: 'vendorName', message: 'Vendor is on the blocked list' }],
          }),
          { status: 400, headers: { 'Content-Type': 'application/json' } },
        )
      }
      return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
    })

    const user = userEvent.setup()
    renderWithProviders(<ExpenseForm />)

    await user.type(screen.getByLabelText(/amount/i), '450.00')
    await user.type(screen.getByLabelText(/vendor/i), 'Swiggy')
    await user.click(screen.getByRole('button', { name: /add expense/i }))

    expect(await screen.findByText(/blocked list/i)).toBeInTheDocument()
  })

  it('offers the category list with Automatic as the default choice', async () => {
    stubFetch()
    renderWithProviders(<ExpenseForm />)

    const select = screen.getByLabelText(/^category$/i) as HTMLSelectElement
    expect(select.value).toBe('')
    expect(await screen.findByRole('option', { name: 'Travel' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /automatic/i })).toBeInTheDocument()
  })
})
