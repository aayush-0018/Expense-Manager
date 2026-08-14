import { describe, expect, it } from 'vitest'
import { formatDate, formatMoney, formatMonth, formatMultiple, shiftMonth } from './format'

describe('formatting', () => {
  it('formats a decimal string as currency without losing precision', () => {
    expect(formatMoney('450.00')).toMatch(/450\.00/)
    expect(formatMoney('1234567.89')).toMatch(/1,?2,?34,?567\.89/)
  })

  it('shows a dash for a missing amount and passes non-numeric values through untouched', () => {
    expect(formatMoney(null)).toBe('—')
    expect(formatMoney(undefined)).toBe('—')
    expect(formatMoney('not-a-number')).toBe('not-a-number')
  })

  it('renders an ISO date without shifting it across a timezone boundary', () => {
    // Parsed as local midnight, so the day never slips backwards.
    expect(formatDate('2026-08-01')).toContain('2026')
    expect(formatDate('2026-08-01')).toMatch(/01/)
  })

  it('formats and shifts months, including across a year boundary', () => {
    expect(formatMonth('2026-08')).toMatch(/Aug/)
    expect(shiftMonth('2026-08', -1)).toBe('2026-07')
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(shiftMonth('2026-08', -5)).toBe('2026-03')
  })

  it('shortens the anomaly multiple to one decimal place', () => {
    expect(formatMultiple('4.20')).toBe('4.2×')
    expect(formatMultiple('21.07')).toBe('21.1×')
  })
})
