import { useI18n } from '../../i18n/I18nProvider'
import { cn } from '../../lib/cn'

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('skeleton rounded-md', className)} />
}

export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  const { t } = useI18n()
  return (
    <div className="flex flex-col gap-3 p-5" role="status" aria-label={t.common.loading}>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton className="h-8 w-7 rounded-t-full rounded-b-[5px]" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-12" />
        </div>
      ))}
    </div>
  )
}
