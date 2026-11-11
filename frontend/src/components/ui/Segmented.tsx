import { useId, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

/** Radio group styled as a segmented control. */
export function Segmented<T extends string>({
  label,
  value,
  onChange,
  options,
  hideLabel,
}: {
  label: string
  /** Keep the label for screen readers only (when a heading right above already says it). */
  hideLabel?: boolean
  value: T
  onChange: (v: T) => void
  options: { value: T; label: ReactNode; hint?: string }[]
}) {
  const name = useId()
  return (
    <fieldset className="flex flex-col gap-1.5">
      <legend className={cn('mb-1.5 text-sm font-medium', hideLabel && 'sr-only')}>{label}</legend>
      <div className="grid auto-cols-fr grid-flow-col gap-1 rounded-control border border-line bg-surface p-1">
        {options.map((o) => (
          <label
            key={o.value}
            className={cn(
              'flex cursor-pointer flex-col items-center justify-center rounded-[7px] px-2 py-2 text-center transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-accent',
              value === o.value ? 'bg-brand text-on-accent' : 'text-muted hover:text-ink',
            )}
          >
            <input type="radio" name={name} value={o.value} checked={value === o.value} onChange={() => onChange(o.value)} className="sr-only" />
            <span className="font-medium">{o.label}</span>
            {o.hint && <span className={cn('text-[0.75rem]', value === o.value ? 'text-on-accent/80' : 'text-faint')}>{o.hint}</span>}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
