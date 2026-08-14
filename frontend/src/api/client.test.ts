import { afterEach, describe, expect, it, vi } from 'vitest'
import { z } from 'zod'
import { ApiError, ContractError, api } from './client'

const schema = z.object({ id: z.number(), name: z.string() })

function stub(response: Response) {
  vi.stubGlobal('fetch', vi.fn(async () => response))
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('api client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('parses a well-formed response', async () => {
    stub(json({ id: 1, name: 'Food' }))
    await expect(api.get('/categories', schema)).resolves.toEqual({ id: 1, name: 'Food' })
  })

  it('surfaces a contract mismatch instead of handing back a malformed object', async () => {
    stub(json({ id: 'not-a-number', name: 'Food' }))
    await expect(api.get('/categories', schema)).rejects.toBeInstanceOf(ContractError)
  })

  it('turns a structured error body into an ApiError carrying the field errors', async () => {
    stub(
      json(
        {
          status: 400,
          error: 'VALIDATION_FAILED',
          message: 'Request validation failed',
          fieldErrors: [{ field: 'amount', message: 'must be greater than 0' }],
        },
        400,
      ),
    )

    const error = await api.post('/expenses', schema, {}).catch((caught) => caught)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.fieldErrors).toEqual([{ field: 'amount', message: 'must be greater than 0' }])
  })

  it('still produces a usable ApiError when the body is not our error shape', async () => {
    stub(new Response('<html>gateway error</html>', { status: 502 }))
    const error = await api.get('/expenses', schema).catch((caught) => caught)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(502)
  })

  it('reports an unreachable backend in plain language', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch')
      }),
    )
    const error = await api.get('/expenses', schema).catch((caught) => caught)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe('NETWORK_ERROR')
    expect(error.message).toMatch(/backend running/i)
  })

  it('omits empty query parameters rather than sending blanks', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      json({ id: 1, name: 'Food' }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await api.get('/expenses', schema, { vendor: '', categoryId: undefined, page: 0, anomalyOnly: true })

    const url = String(fetchMock.mock.calls[0]?.[0])
    expect(url).toContain('page=0')
    expect(url).toContain('anomalyOnly=true')
    expect(url).not.toContain('vendor=')
    expect(url).not.toContain('categoryId=')
  })

  it('treats 204 as success with no body to parse', async () => {
    stub(new Response(null, { status: 204 }))
    await expect(api.delete('/expenses/1')).resolves.toBeUndefined()
  })
})
