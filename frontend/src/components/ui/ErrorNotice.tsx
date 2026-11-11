import { AlertTriangle } from 'lucide-react'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { Button } from './Button'

export function ErrorNotice({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n()
  const errorText = useErrorText()
  return (
    <div role="alert" className="flex flex-col items-start gap-3 p-5 sm:flex-row sm:items-center">
      <AlertTriangle aria-hidden className="size-5 shrink-0 text-accent" />
      <p className="flex-1 text-[0.9375rem] text-muted">{errorText(error)}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          {t.common.tryAgain}
        </Button>
      )}
    </div>
  )
}
