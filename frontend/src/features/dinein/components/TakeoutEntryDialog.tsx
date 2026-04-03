import { useEffect, useState } from 'react'
import { Button } from '../../../components/ui/Button'
import { Card } from '../../../components/ui/Card'

interface TakeoutEntryDialogProps {
  open: boolean
  onClose: () => void
  initialValue?: string
  allowEmpty?: boolean
  confirmLabel?: string
  helperText?: string
  onConfirm: (value: string) => void
}

export function TakeoutEntryDialog({
  open,
  onClose,
  initialValue = '',
  allowEmpty = false,
  confirmLabel = 'Continue',
  helperText = 'Enter a customer name or phone number.',
  onConfirm,
}: TakeoutEntryDialogProps) {
  const [value, setValue] = useState(initialValue)

  useEffect(() => {
    if (open) {
      setValue(initialValue)
    }
  }, [initialValue, open])

  if (!open) {
    return null
  }

  const handleConfirm = () => {
    const normalized = value.trim()
    if (!normalized && !allowEmpty) {
      window.alert('Please enter a customer name or phone number.')
      return
    }
    onConfirm(normalized)
    setValue(initialValue)
  }

  const handleClose = () => {
    setValue(initialValue)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-[rgba(26,28,25,0.18)] p-4 backdrop-blur-sm">
      <Card className="w-full max-w-[30rem] rounded-[24px] p-5">
        <div className="space-y-4">
          <div>
            <h2 className="font-display text-[1.55rem] font-extrabold tracking-[-0.04em] text-[var(--on-surface)]">
              Takeout / 外带
            </h2>
            <p className="mt-1 text-[0.92rem] text-[var(--muted)]">
              {helperText}
            </p>
          </div>

          <label className="block space-y-2">
            <span className="text-[0.78rem] font-semibold uppercase tracking-[0.12em] text-[var(--muted)]">
              Customer Name or Phone
            </span>
            <input
              value={value}
              onChange={(event) => setValue(event.target.value)}
              placeholder="Optional name / phone"
              className="min-h-12 w-full rounded-[16px] bg-[var(--surface-container-lowest)] px-4 text-[0.95rem] text-[var(--on-surface)] shadow-[inset_0_0_0_1px_rgba(26,28,25,0.06)] outline-none focus:shadow-[inset_0_0_0_2px_rgba(97,0,0,0.22)]"
            />
          </label>

          <div className="grid grid-cols-2 gap-3">
            <Button variant="secondary" className="min-h-11 rounded-[16px]" onClick={handleClose}>
              Cancel
            </Button>
            <Button className="min-h-11 rounded-[16px]" onClick={handleConfirm}>
              {confirmLabel}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
