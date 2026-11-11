import type { ReactNode } from 'react'

export function PageHeader({ title, description, action }: { title: ReactNode; description?: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:mb-8 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="font-display text-[1.875rem] leading-[1.1] font-bold tracking-[-0.025em] sm:text-[2.25rem]">{title}</h1>
        {description && <p className="mt-2 max-w-[60ch] text-[0.9375rem] text-muted">{description}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  )
}
