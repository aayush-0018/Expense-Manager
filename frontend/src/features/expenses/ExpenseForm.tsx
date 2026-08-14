import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui'
import { useCategories, useCreateExpense, useUpdateExpense } from '@/hooks/queries'
import { todayIso } from '@/lib/format'
import type { Expense } from '@/types/schemas'

/**
 * Client-side rules deliberately mirror the server's Bean Validation constraints. The server
 * remains the authority - anything it rejects is mapped back onto the offending field below -
 * but repeating the rules here means the common mistakes never cost a round trip.
 */
const formSchema = z.object({
  date: z
    .string()
    .min(1, 'Date is required')
    .refine((value) => value <= todayIso(), 'Date cannot be in the future')
    .refine((value) => value >= '2000-01-01', 'Date must be on or after 2000-01-01'),
  amount: z
    .string()
    .min(1, 'Amount is required')
    .regex(/^\d+(\.\d{1,2})?$/, 'Enter a positive amount with at most 2 decimal places')
    .refine((value) => Number(value) > 0, 'Amount must be greater than 0')
    .refine((value) => Number(value) <= 10_000_000, 'Amount must not exceed 10,000,000'),
  vendorName: z.string().trim().min(1, 'Vendor name is required').max(120, 'Vendor name is too long'),
  description: z.string().max(500, 'Description must be at most 500 characters').optional(),
  categoryId: z.string().optional(),
})

type FormValues = z.infer<typeof formSchema>

const FIELD_NAMES = ['date', 'amount', 'vendorName', 'description', 'categoryId'] as const

/**
 * Built fresh on every reset rather than held as a module constant: the default date is
 * "today", and a constant would freeze it at import time — a tab left open overnight would
 * quietly start defaulting to yesterday.
 */
function emptyValues(): FormValues {
  return {
    date: todayIso(),
    amount: '',
    vendorName: '',
    description: '',
    categoryId: '',
  }
}

export function ExpenseForm({ editing, onDone }: { editing?: Expense | null; onDone?: () => void }) {
  const categories = useCategories()
  const create = useCreateExpense()
  const update = useUpdateExpense()
  const pending = create.isPending || update.isPending

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(formSchema), defaultValues: emptyValues() })

  useEffect(() => {
    reset(
      editing
        ? {
            date: editing.date,
            amount: editing.amount,
            vendorName: editing.vendorName,
            description: editing.description ?? '',
            // Only a genuine override is pre-selected; a rule-assigned category stays on
            // "Automatic" so re-saving does not silently freeze it into a manual override.
            categoryId: editing.categorizationSource === 'MANUAL_OVERRIDE' ? String(editing.category.id) : '',
          }
        : emptyValues(),
    )
  }, [editing, reset])

  const onSubmit = handleSubmit(async (values) => {
    const input = {
      date: values.date,
      amount: values.amount,
      vendorName: values.vendorName.trim(),
      description: values.description?.trim() ? values.description.trim() : undefined,
      categoryId: values.categoryId ? Number(values.categoryId) : null,
    }

    try {
      if (editing) {
        await update.mutateAsync({ id: editing.id, input })
      } else {
        await create.mutateAsync(input)
        reset(emptyValues())
      }
      onDone?.()
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        // Server-side validation lands on the field that caused it, not in a generic banner.
        for (const fieldError of error.fieldErrors) {
          if ((FIELD_NAMES as readonly string[]).includes(fieldError.field)) {
            setError(fieldError.field as keyof FormValues, { message: fieldError.message })
          }
        }
      } else {
        setError('root', { message: error instanceof Error ? error.message : 'Could not save the expense' })
      }
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4 sm:grid-cols-2 lg:grid-cols-12">
      <div className="lg:col-span-2">
        <label className="field-label" htmlFor="date">
          Date
        </label>
        <input id="date" type="date" className="field-input" {...register('date')} />
        {errors.date && <p className="field-error">{errors.date.message}</p>}
      </div>

      <div className="lg:col-span-2">
        <label className="field-label" htmlFor="amount">
          Amount (₹)
        </label>
        <input
          id="amount"
          inputMode="decimal"
          placeholder="450.00"
          className="field-input tnum"
          {...register('amount')}
        />
        {errors.amount && <p className="field-error">{errors.amount.message}</p>}
      </div>

      <div className="lg:col-span-3">
        <label className="field-label" htmlFor="vendorName">
          Vendor
        </label>
        <input id="vendorName" placeholder="Swiggy" className="field-input" {...register('vendorName')} />
        {errors.vendorName ? (
          <p className="field-error">{errors.vendorName.message}</p>
        ) : (
          <p className="mt-1.5 text-xs text-ink-400">Category is assigned automatically from this name</p>
        )}
      </div>

      <div className="lg:col-span-3">
        <label className="field-label" htmlFor="description">
          Description
        </label>
        <input id="description" placeholder="Team lunch" className="field-input" {...register('description')} />
        {errors.description && <p className="field-error">{errors.description.message}</p>}
      </div>

      <div className="lg:col-span-2">
        <label className="field-label" htmlFor="categoryId">
          Category
        </label>
        <select id="categoryId" className="field-input" {...register('categoryId')}>
          <option value="">Automatic</option>
          {categories.data?.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
      </div>

      <div className="flex items-end gap-2 sm:col-span-2 lg:col-span-12">
        <Button type="submit" disabled={pending}>
          {pending ? 'Saving…' : editing ? 'Save changes' : 'Add expense'}
        </Button>
        {editing && (
          <Button type="button" variant="secondary" onClick={onDone} disabled={pending}>
            Cancel
          </Button>
        )}
        {errors.root && (
          <p role="alert" className="text-sm text-rose-600">
            {errors.root.message}
          </p>
        )}
      </div>
    </form>
  )
}
