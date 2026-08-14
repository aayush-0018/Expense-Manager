import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ExpenseTable } from './ExpenseTable'
import { makeExpense, renderWithProviders } from '@/test/utils'

describe('ExpenseTable', () => {
  it('renders the expense with a formatted amount and its category', () => {
    renderWithProviders(
      <ExpenseTable expenses={[makeExpense()]} onEdit={vi.fn()} onDelete={vi.fn()} />,
    )

    expect(screen.getByText('Swiggy')).toBeInTheDocument()
    expect(screen.getByText('Food')).toBeInTheDocument()
    expect(screen.getByText('Team lunch')).toBeInTheDocument()
    expect(screen.getByText(/450\.00/)).toBeInTheDocument()
  })

  it('marks an anomalous row distinctly, and not by colour alone', () => {
    renderWithProviders(
      <ExpenseTable
        expenses={[makeExpense({ id: 2, amount: '9800.00', isAnomaly: true })]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    // The row is labelled for assistive tech...
    const row = screen.getByRole('row', { name: /anomalous expense/i })
    expect(row).toBeInTheDocument()
    // ...and carries a visible text badge, not just a tint.
    expect(within(row).getByText(/anomaly/i)).toBeInTheDocument()
  })

  it('leaves a normal row unlabelled and unbadged', () => {
    renderWithProviders(
      <ExpenseTable expenses={[makeExpense()]} onEdit={vi.fn()} onDelete={vi.fn()} />,
    )

    expect(screen.queryByRole('row', { name: /anomalous expense/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/anomaly/i)).not.toBeInTheDocument()
  })

  it('explains how a category was assigned when it was not a rule match', () => {
    renderWithProviders(
      <ExpenseTable
        expenses={[
          makeExpense({ id: 1, categorizationSource: 'DEFAULT' }),
          makeExpense({ id: 2, categorizationSource: 'MANUAL_OVERRIDE' }),
        ]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText(/no vendor rule matched/i)).toBeInTheDocument()
    expect(screen.getByText(/category set manually/i)).toBeInTheDocument()
  })

  it('passes the expense back when edit or delete is pressed', async () => {
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    const expense = makeExpense()
    renderWithProviders(<ExpenseTable expenses={[expense]} onEdit={onEdit} onDelete={onDelete} />)

    await userEvent.click(screen.getByRole('button', { name: /edit/i }))
    expect(onEdit).toHaveBeenCalledWith(expense)

    await userEvent.click(screen.getByRole('button', { name: /delete/i }))
    expect(onDelete).toHaveBeenCalledWith(expense)
  })
})
