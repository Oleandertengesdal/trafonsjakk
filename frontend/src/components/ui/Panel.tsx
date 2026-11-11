import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '../../lib/cn'

/** The basic surface. `title` renders a small heading row with optional action on the right. */
export function Panel({
  title,
  action,
  className,
  children,
  ...rest
}: HTMLAttributes<HTMLElement> & { title?: ReactNode; action?: ReactNode }) {
  return (
    <section className={cn('overflow-hidden rounded-panel border border-line bg-surface shadow-[0_1px_0_rgb(0_0_0/0.03)]', className)} {...rest}>
      {(title || action) && (
        <header className="flex items-center justify-between gap-3 px-5 pt-4 pb-1">
          {title && <h2 className="font-display text-[1.0625rem] font-semibold tracking-[-0.01em]">{title}</h2>}
          {action}
        </header>
      )}
      {children}
    </section>
  )
}
