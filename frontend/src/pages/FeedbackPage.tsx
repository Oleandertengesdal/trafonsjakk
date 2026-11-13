import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Check, Copy, Mail } from 'lucide-react'
import { useI18n } from '../i18n/I18nProvider'
import { SUPPORT_EMAIL } from '../lib/contact'
import { safeAppPath } from '../lib/safePath'
import { ErrorReference } from '../components/errors/ErrorScene'
import { AuthLayout } from './AuthLayout'

/** "Found a problem?": how to email the admin. Public, so it also helps people who can't log in. */
export function FeedbackPage() {
  const { t } = useI18n()
  const f = t.feedback
  const [params] = useSearchParams()
  const from = safeAppPath(params.get('from'))
  // An error code from an error page; only the shape the app makes is accepted
  const ref = /^[A-Z0-9]{4,12}$/.test(params.get('ref') ?? '') ? params.get('ref') : null
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(false), 2500)
    return () => clearTimeout(timer)
  }, [copied])

  const body = [f.bodyWhat, '', '', '', from ? f.bodyPage(window.location.origin + from) : null, ref ? f.bodyRef(ref) : null]
    .filter((l) => l !== null)
    .join('\n')
  // encodeURIComponent, not URLSearchParams: some mail apps show a "+" instead of a space
  const mailto = `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(f.subject)}&body=${encodeURIComponent(body)}`

  async function copy() {
    try {
      await navigator.clipboard.writeText(SUPPORT_EMAIL)
      setCopied(true)
    } catch {
      /* clipboard not available: the address is on screen to copy by hand */
    }
  }

  return (
    <AuthLayout
      title={f.title}
      subtitle={f.subtitle}
      showFeedbackLink={false}
      footer={
        <Link to="/" className="font-medium text-accent hover:underline">
          {f.back}
        </Link>
      }
    >
      <p className="font-medium">{f.helps}</p>
      <ul className="mt-2 flex list-disc flex-col gap-1 pl-5 text-muted marker:text-faint">
        <li>{f.help1}</li>
        <li>{f.help2}</li>
        <li>{f.help3}</li>
      </ul>

      {ref && (
        <div className="mt-5">
          <ErrorReference value={ref} />
          <p className="mt-2 text-sm text-muted">{f.refHint}</p>
        </div>
      )}

      <a
        href={mailto}
        className="mt-7 inline-flex h-12 w-full items-center justify-center gap-2 rounded-control bg-brand px-5 font-medium text-on-accent shadow-[inset_0_-2px_0_rgb(0_0_0/0.16)] transition-[background-color,transform] hover:bg-brand-hover active:translate-y-px"
      >
        <Mail aria-hidden className="size-4.5" />
        {f.write}
      </a>

      <div className="mt-5 rounded-control border border-line bg-surface px-4 py-3">
        <p className="text-sm text-muted">{f.orSendTo}</p>
        <div className="mt-1 flex items-center justify-between gap-3">
          <span className="min-w-0 font-medium break-all select-all">{SUPPORT_EMAIL}</span>
          <button
            type="button"
            onClick={() => void copy()}
            className="inline-flex shrink-0 items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm text-muted transition-colors hover:bg-raised hover:text-ink"
          >
            {copied ? <Check aria-hidden className="size-4 text-accent" /> : <Copy aria-hidden className="size-4" />}
            {copied ? f.copied : f.copy}
          </button>
        </div>
      </div>
    </AuthLayout>
  )
}
