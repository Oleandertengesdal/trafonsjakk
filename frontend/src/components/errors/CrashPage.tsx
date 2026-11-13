import { RotateCw } from 'lucide-react'
import { useI18n } from '../../i18n/I18nProvider'
import { isStaleChunkError } from '../../lib/staleChunks'
import { Button, ButtonAnchor } from '../ui/Button'
import { feedbackHref } from '../../lib/errorReporting'
import { ErrorScene } from './ErrorScene'

/**
 * Shown when a page crashes. A missing chunk after a deploy is a new version, not a crash, and says so.
 * Plain links rather than router links: this may be all that's left of the app, and a full reload
 * starts from a clean slate anyway.
 */
export function CrashPage({ error, reference }: { error: unknown; reference: string }) {
  const e = useI18n().t.errorPage
  const update = isStaleChunkError(error)
  const reload = (
    <Button onClick={() => window.location.reload()}>
      <RotateCw aria-hidden className="size-4.5" />
      {e.reload}
    </Button>
  )
  if (update) {
    return (
      <ErrorScene lights="on" title={e.update.title} body={e.update.body}>
        {reload}
      </ErrorScene>
    )
  }
  return (
    <ErrorScene lights="off" title={e.crash.title} body={e.crash.body} reference={reference}>
      {reload}
      <ButtonAnchor href="/" variant="secondary">
        {e.home}
      </ButtonAnchor>
      <ButtonAnchor href={feedbackHref(reference)} variant="ghost">
        {e.report}
      </ButtonAnchor>
    </ErrorScene>
  )
}
