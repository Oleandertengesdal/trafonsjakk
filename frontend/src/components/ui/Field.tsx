import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { cn } from '../../lib/cn'

const control =
  'w-full rounded-control border border-line bg-surface px-3 text-ink placeholder:text-faint transition-colors focus:border-accent focus:outline-none aria-[invalid=true]:border-accent'

interface FieldProps {
  label: string
  hint?: ReactNode
  error?: string
  children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode
}

export function Field({ label, hint, error, children }: FieldProps) {
  const id = useId()
  const descId = `${id}-desc`
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      {children({ id, 'aria-invalid': !!error, 'aria-describedby': hint || error ? descId : undefined })}
      {(error || hint) && (
        <p id={descId} className={cn('text-[0.8125rem]', error ? 'text-accent' : 'text-muted')}>
          {error ?? hint}
        </p>
      )}
    </div>
  )
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, ...rest },
  ref,
) {
  return <input ref={ref} className={cn(control, 'h-11', className)} {...rest} />
})

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { className, ...rest },
  ref,
) {
  return (
    <span className="relative block">
      <select ref={ref} className={cn(control, 'h-11 appearance-none pr-9', className)} {...rest} />
      <svg aria-hidden viewBox="0 0 12 12" className="pointer-events-none absolute top-1/2 right-3 size-3 -translate-y-1/2 text-muted">
        <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  )
})

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function Textarea({ className, ...rest }, ref) {
    return <textarea ref={ref} className={cn(control, 'min-h-24 py-2.5 leading-relaxed', className)} {...rest} />
  },
)
