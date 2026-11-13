import { Link } from 'react-router'
import { AlertTriangle, WifiOff } from 'lucide-react'
import { ApiError } from '../../api/client'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { feedbackHref } from '../../lib/errorReporting'
import { ErrorReference } from '../errors/ErrorScene'
import { Button } from './Button'

/**
 * An error in place of something that didn't load: what went wrong in plain words, a retry, and for
 * unexpected server errors the error code to quote and a link to report it.
 */
export function ErrorNotice({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n()
  const errorText = useErrorText()
  const ref = error instanceof ApiError ? error.ref : null
  const offline = error instanceof ApiError && error.status === 0
  const Icon = offline ? WifiOff : AlertTriangle
  return (
    <div role="alert" className="flex flex-col items-start gap-3 p-5 sm:flex-row sm:items-center">
      <Icon aria-hidden className="size-5 shrink-0 text-accent" />
      <div className="flex-1">
        <p className="text-[0.9375rem] text-muted">{errorText(error)}</p>
        {ref && (
          <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-2">
            <ErrorReference value={ref} />
            <Link to={feedbackHref(ref)} className="text-sm font-medium text-accent underline-offset-4 hover:underline">
              {t.errorPage.report}
            </Link>
          </div>
        )}
      </div>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          {t.common.tryAgain}
        </Button>
      )}
    </div>
  )
}
