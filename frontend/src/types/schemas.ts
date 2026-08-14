import { z } from 'zod'

/**
 * Every API response is parsed through these schemas before it reaches a component.
 *
 * The point is to fail loudly at the boundary: if the backend contract shifts, the error
 * surfaces here with the field name, rather than as `undefined` several layers deep inside a
 * chart. TypeScript types are inferred from the schemas, so the two can never drift apart.
 *
 * Money always arrives as a decimal *string* and is kept that way. JavaScript numbers cannot
 * represent every 2-decimal value exactly, and the UI only ever formats amounts - it never
 * does arithmetic on them.
 */

export const categorySchema = z.object({
  id: z.number(),
  name: z.string(),
  colorHex: z.string().nullable(),
  isDefault: z.boolean(),
})

export const categorizationSourceSchema = z.enum(['RULE', 'DEFAULT', 'MANUAL_OVERRIDE'])

export const expenseSchema = z.object({
  id: z.number(),
  date: z.string(),
  amount: z.string(),
  vendorName: z.string(),
  description: z.string().nullable(),
  category: categorySchema,
  categorizationSource: categorizationSourceSchema,
  isAnomaly: z.boolean(),
  anomalyReason: z.string().nullable(),
  importBatchId: z.number().nullable(),
  createdAt: z.string().nullable(),
})

export const pageSchema = <T extends z.ZodTypeAny>(item: T) =>
  z.object({
    content: z.array(item),
    page: z.number(),
    size: z.number(),
    totalElements: z.number(),
    totalPages: z.number(),
    hasNext: z.boolean(),
  })

export const expensePageSchema = pageSchema(expenseSchema)

export const importResultSchema = z.object({
  batchId: z.number(),
  filename: z.string(),
  totalRows: z.number(),
  importedRows: z.number(),
  failedRows: z.number(),
  status: z.enum(['COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED']),
  errors: z.array(
    z.object({
      row: z.number(),
      field: z.string().nullable(),
      message: z.string(),
    }),
  ),
  warnings: z.array(z.object({ row: z.number(), message: z.string() })),
})

export const importFormatSchema = z.object({
  templateHeader: z.string(),
  acceptedDateFormats: z.array(z.string()),
  requiredColumns: z.array(z.string()),
  optionalColumns: z.array(z.string()),
  columnAliases: z.record(z.string(), z.array(z.string())),
  notes: z.array(z.string()),
})

export const dashboardSummarySchema = z.object({
  month: z.string().nullable(),
  totalAmount: z.string(),
  expenseCount: z.number(),
  anomalyCount: z.number(),
  topCategoryName: z.string().nullable(),
  topCategoryAmount: z.string().nullable(),
})

export const monthlyByCategorySchema = z.object({
  months: z.array(z.string()),
  series: z.array(
    z.object({
      categoryId: z.number(),
      categoryName: z.string(),
      colorHex: z.string().nullable(),
      totals: z.array(z.string()),
    }),
  ),
})

export const topVendorsSchema = z.object({
  vendors: z.array(
    z.object({
      vendorName: z.string(),
      totalAmount: z.string(),
      expenseCount: z.number(),
      topCategory: z.string().nullable(),
    }),
  ),
})

export const anomalyItemSchema = z.object({
  expense: expenseSchema,
  categoryAverage: z.string(),
  threshold: z.string(),
  timesAverage: z.string(),
})

export const anomalyPageSchema = pageSchema(anomalyItemSchema)

export const apiErrorSchema = z.object({
  timestamp: z.string().optional(),
  status: z.number(),
  error: z.string(),
  message: z.string(),
  path: z.string().optional(),
  fieldErrors: z
    .array(z.object({ field: z.string(), message: z.string() }))
    .nullable()
    .optional(),
})

export type Category = z.infer<typeof categorySchema>
export type Expense = z.infer<typeof expenseSchema>
export type ExpensePage = z.infer<typeof expensePageSchema>
export type ImportResult = z.infer<typeof importResultSchema>
export type ImportFormat = z.infer<typeof importFormatSchema>
export type DashboardSummary = z.infer<typeof dashboardSummarySchema>
export type MonthlyByCategory = z.infer<typeof monthlyByCategorySchema>
export type TopVendors = z.infer<typeof topVendorsSchema>
export type AnomalyItem = z.infer<typeof anomalyItemSchema>
export type AnomalyPage = z.infer<typeof anomalyPageSchema>
export type ApiErrorBody = z.infer<typeof apiErrorSchema>
