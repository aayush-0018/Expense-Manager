import { z } from 'zod'
import { apiErrorSchema, type ApiErrorBody } from '@/types/schemas'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

/**
 * A failed request, carrying the backend's structured error body when there was one.
 *
 * `fieldErrors` is what lets a form map a server-side validation failure back onto the
 * specific input that caused it, instead of showing one generic banner.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: { field: string; message: string }[]

  constructor(status: number, code: string, message: string, fieldErrors: ApiErrorBody['fieldErrors']) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors ?? []
  }
}

/** Thrown when a response does not match its schema - a contract drift, not a user error. */
export class ContractError extends Error {
  constructor(path: string, cause: unknown) {
    super(`The response from ${path} did not match the expected shape`)
    this.name = 'ContractError'
    this.cause = cause
  }
}

function buildUrl(path: string, params?: Record<string, string | number | boolean | undefined | null>): string {
  const url = `${BASE_URL}${path}`
  if (!params) return url
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, String(value))
    }
  }
  const query = search.toString()
  return query ? `${url}?${query}` : url
}

async function toApiError(response: Response, path: string): Promise<ApiError> {
  let body: unknown
  try {
    body = await response.json()
  } catch {
    return new ApiError(response.status, 'UNKNOWN', `${response.status} ${response.statusText}`, [])
  }
  const parsed = apiErrorSchema.safeParse(body)
  if (!parsed.success) {
    return new ApiError(response.status, 'UNKNOWN', `Request to ${path} failed (${response.status})`, [])
  }
  return new ApiError(parsed.data.status, parsed.data.error, parsed.data.message, parsed.data.fieldErrors)
}

async function send<T>(path: string, schema: z.ZodType<T>, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, init)
  } catch (cause) {
    // fetch only rejects for network-level failures; surface that as something readable.
    throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach the server. Is the backend running?', [])
  }

  if (!response.ok) {
    throw await toApiError(response, path)
  }
  if (response.status === 204) {
    return undefined as T
  }

  const body = await response.json()
  const parsed = schema.safeParse(body)
  if (!parsed.success) {
    throw new ContractError(path, parsed.error)
  }
  return parsed.data
}

export const api = {
  get<T>(path: string, schema: z.ZodType<T>, params?: Record<string, string | number | boolean | undefined | null>) {
    return send(buildUrl(path, params), schema)
  },

  post<T>(path: string, schema: z.ZodType<T>, body: unknown) {
    return send(buildUrl(path), schema, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  },

  put<T>(path: string, schema: z.ZodType<T>, body: unknown) {
    return send(buildUrl(path), schema, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  },

  delete(path: string) {
    return send(buildUrl(path), z.undefined(), { method: 'DELETE' })
  },

  upload<T>(path: string, schema: z.ZodType<T>, file: File) {
    const form = new FormData()
    form.append('file', file)
    // Content-Type is intentionally unset: the browser must add the multipart boundary.
    return send(buildUrl(path), schema, { method: 'POST', body: form })
  },
}
