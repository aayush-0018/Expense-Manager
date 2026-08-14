import { z } from 'zod'
import { api } from './client'
import {
  anomalyPageSchema,
  categorySchema,
  dashboardSummarySchema,
  expensePageSchema,
  expenseSchema,
  importFormatSchema,
  importResultSchema,
  monthlyByCategorySchema,
  topVendorsSchema,
} from '@/types/schemas'

export interface ExpenseFilters {
  from?: string
  to?: string
  categoryId?: number
  vendor?: string
  anomalyOnly?: boolean
  page?: number
  size?: number
  sort?: string
}

export interface ExpenseInput {
  date: string
  amount: string
  vendorName: string
  description?: string
  categoryId?: number | null
}

export const expensesApi = {
  list: (filters: ExpenseFilters) =>
    api.get('/expenses', expensePageSchema, {
      from: filters.from,
      to: filters.to,
      categoryId: filters.categoryId,
      vendor: filters.vendor,
      anomalyOnly: filters.anomalyOnly ? true : undefined,
      page: filters.page ?? 0,
      size: filters.size ?? 25,
      sort: filters.sort ?? 'expenseDate,desc',
    }),

  create: (input: ExpenseInput) => api.post('/expenses', expenseSchema, input),

  update: (id: number, input: ExpenseInput) => api.put(`/expenses/${id}`, expenseSchema, input),

  remove: (id: number) => api.delete(`/expenses/${id}`),

  importCsv: (file: File) => api.upload('/expenses/import', importResultSchema, file),

  importFormat: () => api.get('/expenses/import/format', importFormatSchema),
}

export const dashboardApi = {
  summary: (month?: string) => api.get('/dashboard/summary', dashboardSummarySchema, { month }),

  monthlyByCategory: (from?: string, to?: string) =>
    api.get('/dashboard/monthly-by-category', monthlyByCategorySchema, { from, to }),

  topVendors: (month?: string, limit = 5) =>
    api.get('/dashboard/top-vendors', topVendorsSchema, { month, limit }),

  anomalies: (page = 0, size = 20) => api.get('/dashboard/anomalies', anomalyPageSchema, { page, size }),
}

export const categoriesApi = {
  list: () => api.get('/categories', z.array(categorySchema)),
}
