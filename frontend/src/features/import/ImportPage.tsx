import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, Card, cx } from '@/components/ui'
import { useImportCsv, useImportFormat } from '@/hooks/queries'
import type { ImportResult } from '@/types/schemas'

export function ImportPage() {
  const [dragging, setDragging] = useState(false)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const format = useImportFormat()
  const upload = useImportCsv()

  async function handleFile(file: File | undefined) {
    if (!file) return
    setResult(null)
    setFailure(null)
    try {
      setResult(await upload.mutateAsync(file))
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'The upload failed')
    } finally {
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  function downloadTemplate() {
    const header = format.data?.templateHeader ?? 'date,amount,vendor,description'
    const csv = `${header}\n2026-08-01,450.00,Swiggy,Team lunch\n2026-08-02,1200.00,Uber,Airport drop\n`
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'expense-template.csv'
    anchor.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="grid gap-5 lg:grid-cols-3">
      <div className="space-y-5 lg:col-span-2">
        <Card title="Upload a CSV">
          <div
            onDragOver={(event) => {
              event.preventDefault()
              setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault()
              setDragging(false)
              void handleFile(event.dataTransfer.files?.[0])
            }}
            className={cx(
              'flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed px-6 py-12 text-center transition-colors',
              dragging ? 'border-ink-500 bg-ink-100' : 'border-ink-200 bg-ink-50',
            )}
          >
            <p className="text-sm font-semibold text-ink-800">
              {upload.isPending ? 'Importing…' : 'Drop a .csv file here'}
            </p>
            <p className="max-w-sm text-sm text-ink-500">
              Rows that fail validation are reported below and skipped — the rest of the file still imports.
            </p>
            <div className="mt-1 flex gap-2">
              <Button onClick={() => inputRef.current?.click()} disabled={upload.isPending}>
                Choose file
              </Button>
              <Button variant="secondary" onClick={downloadTemplate}>
                Download template
              </Button>
            </div>
            <input
              ref={inputRef}
              type="file"
              accept=".csv,text/csv"
              className="sr-only"
              onChange={(event) => void handleFile(event.target.files?.[0])}
            />
          </div>

          {failure && (
            <div role="alert" className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
              {failure}
            </div>
          )}
        </Card>

        {result && <ImportResultPanel result={result} />}
      </div>

      <Card title="Expected format">
        <dl className="space-y-4 text-sm">
          <div>
            <dt className="font-medium text-ink-800">Header row</dt>
            <dd className="mt-1 rounded-md bg-ink-100 px-2.5 py-1.5 font-mono text-xs text-ink-700">
              {format.data?.templateHeader ?? 'date,amount,vendor,description'}
            </dd>
          </div>
          <div>
            <dt className="font-medium text-ink-800">Required columns</dt>
            <dd className="mt-1 text-ink-600">{(format.data?.requiredColumns ?? ['date', 'amount', 'vendor']).join(', ')}</dd>
          </div>
          <div>
            <dt className="font-medium text-ink-800">Optional columns</dt>
            <dd className="mt-1 text-ink-600">
              {(format.data?.optionalColumns ?? ['description', 'category']).join(', ')}
            </dd>
          </div>
          <div>
            <dt className="font-medium text-ink-800">Date formats</dt>
            <dd className="mt-1 font-mono text-xs text-ink-600">
              {(format.data?.acceptedDateFormats ?? ['yyyy-MM-dd']).join(' · ')}
            </dd>
          </div>
          {format.data?.notes && (
            <div>
              <dt className="font-medium text-ink-800">Notes</dt>
              <dd className="mt-1">
                <ul className="list-disc space-y-1 pl-4 text-ink-600">
                  {format.data.notes.map((note) => (
                    <li key={note}>{note}</li>
                  ))}
                </ul>
              </dd>
            </div>
          )}
        </dl>
      </Card>
    </div>
  )
}

function ImportResultPanel({ result }: { result: ImportResult }) {
  const clean = result.failedRows === 0

  function copyErrors() {
    const text = result.errors.map((error) => `Line ${error.row} · ${error.field ?? '—'} · ${error.message}`).join('\n')
    void navigator.clipboard.writeText(text)
  }

  return (
    <Card
      title={`Import result · ${result.filename}`}
      action={
        <span
          className={cx(
            'rounded-full px-2.5 py-0.5 text-xs font-semibold',
            clean ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800',
          )}
        >
          {clean ? 'Completed' : 'Completed with errors'}
        </span>
      }
    >
      <div className="grid grid-cols-3 gap-3">
        <Figure label="Rows read" value={result.totalRows} />
        <Figure label="Imported" value={result.importedRows} tone="good" />
        <Figure label="Failed" value={result.failedRows} tone={result.failedRows > 0 ? 'bad' : undefined} />
      </div>

      {result.errors.length > 0 && (
        <details open className="mt-5">
          <summary className="cursor-pointer text-sm font-medium text-ink-800">
            {result.errors.length} row{result.errors.length === 1 ? '' : 's'} could not be imported
          </summary>
          <div className="mt-3 overflow-hidden rounded-lg border border-ink-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-ink-50 text-xs uppercase tracking-wide text-ink-500">
                <tr>
                  <th className="px-3 py-2 font-medium">Line</th>
                  <th className="px-3 py-2 font-medium">Field</th>
                  <th className="px-3 py-2 font-medium">Problem</th>
                </tr>
              </thead>
              <tbody>
                {result.errors.map((error) => (
                  <tr key={`${error.row}-${error.field}`} className="border-t border-ink-100">
                    <td className="tnum px-3 py-2 text-ink-600">{error.row}</td>
                    <td className="px-3 py-2 font-mono text-xs text-ink-700">{error.field ?? '—'}</td>
                    <td className="px-3 py-2 text-ink-700">{error.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Button variant="secondary" size="sm" className="mt-3" onClick={copyErrors}>
            Copy errors
          </Button>
        </details>
      )}

      {result.warnings.length > 0 && (
        <div className="mt-5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
          <p className="text-sm font-medium text-amber-900">
            {result.warnings.length} possible duplicate{result.warnings.length === 1 ? '' : 's'}
          </p>
          <p className="mt-0.5 text-xs text-amber-800">
            These were imported anyway — two identical purchases on one day are a normal thing to record.
          </p>
          <ul className="mt-2 space-y-1 text-sm text-amber-900">
            {result.warnings.map((warning) => (
              <li key={warning.row}>
                <span className="tnum font-medium">Line {warning.row}</span> · {warning.message}
              </li>
            ))}
          </ul>
        </div>
      )}

      {result.importedRows > 0 && (
        <div className="mt-5 flex gap-2">
          <Link to="/">
            <Button variant="secondary">View expenses</Button>
          </Link>
          <Link to="/dashboard">
            <Button variant="secondary">Open dashboard</Button>
          </Link>
        </div>
      )}
    </Card>
  )
}

function Figure({ label, value, tone }: { label: string; value: number; tone?: 'good' | 'bad' }) {
  return (
    <div className="rounded-lg border border-ink-200 px-4 py-3">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</p>
      <p
        className={cx(
          'tnum mt-1 text-xl font-semibold',
          tone === 'good' ? 'text-emerald-700' : tone === 'bad' ? 'text-rose-700' : 'text-ink-900',
        )}
      >
        {value}
      </p>
    </div>
  )
}
