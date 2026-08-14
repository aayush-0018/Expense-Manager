import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  categoriesApi,
  dashboardApi,
  expensesApi,
  type ExpenseFilters,
  type ExpenseInput,
} from '@/api/endpoints'

/**
 * Query keys. `expenses` and `dashboard` are separate roots but are always invalidated
 * together after a write: adding, editing, importing or deleting an expense shifts a category
 * average, which can flip the anomaly flag on rows the user never touched. Invalidating only
 * the list would leave the dashboard counts quietly disagreeing with the table.
 */
export const queryKeys = {
  expenses: (filters: ExpenseFilters) => ['expenses', filters] as const,
  categories: () => ['categories'] as const,
  importFormat: () => ['import-format'] as const,
  dashboardSummary: (month?: string) => ['dashboard', 'summary', month ?? 'all'] as const,
  dashboardMonthly: (from?: string, to?: string) => ['dashboard', 'monthly', from, to] as const,
  dashboardVendors: (month?: string, limit?: number) => ['dashboard', 'vendors', month ?? 'all', limit] as const,
  dashboardAnomalies: (page: number, size: number) => ['dashboard', 'anomalies', page, size] as const,
}

function useInvalidateAll() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['expenses'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }
}

export function useExpenses(filters: ExpenseFilters) {
  return useQuery({
    queryKey: queryKeys.expenses(filters),
    queryFn: () => expensesApi.list(filters),
    placeholderData: (previous) => previous, // keeps the table steady while filters change
  })
}

export function useCategories() {
  return useQuery({
    queryKey: queryKeys.categories(),
    queryFn: categoriesApi.list,
    staleTime: 5 * 60 * 1000, // categories are seed data; refetching them constantly is noise
  })
}

export function useCreateExpense() {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: (input: ExpenseInput) => expensesApi.create(input),
    onSuccess: invalidate,
  })
}

export function useUpdateExpense() {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: ExpenseInput }) => expensesApi.update(id, input),
    onSuccess: invalidate,
  })
}

export function useDeleteExpense() {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: (id: number) => expensesApi.remove(id),
    onSuccess: invalidate,
  })
}

export function useImportCsv() {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: (file: File) => expensesApi.importCsv(file),
    onSuccess: invalidate,
  })
}

export function useImportFormat() {
  return useQuery({
    queryKey: queryKeys.importFormat(),
    queryFn: expensesApi.importFormat,
    staleTime: Infinity,
  })
}

export function useDashboardSummary(month?: string) {
  return useQuery({
    queryKey: queryKeys.dashboardSummary(month),
    queryFn: () => dashboardApi.summary(month),
  })
}

export function useMonthlyByCategory(from?: string, to?: string) {
  return useQuery({
    queryKey: queryKeys.dashboardMonthly(from, to),
    queryFn: () => dashboardApi.monthlyByCategory(from, to),
  })
}

export function useTopVendors(month?: string, limit = 5) {
  return useQuery({
    queryKey: queryKeys.dashboardVendors(month, limit),
    queryFn: () => dashboardApi.topVendors(month, limit),
  })
}

export function useAnomalies(page = 0, size = 20) {
  return useQuery({
    queryKey: queryKeys.dashboardAnomalies(page, size),
    queryFn: () => dashboardApi.anomalies(page, size),
  })
}
