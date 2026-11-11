import type { ReactNode } from 'react'

export function EmptyState({ icon, title, children, action }: { icon?: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      {icon && <div className="mb-4 text-faint [&_svg]:size-8">{icon}</div>}
      <p className="font-display text-lg font-semibold">{title}</p>
      {children && <p className="mt-1.5 max-w-sm text-[0.9375rem] text-muted">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}
