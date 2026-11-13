import { useEffect, useState, type ReactNode } from 'react'
import { Check, Copy } from 'lucide-react'
import { useI18n } from '../../i18n/I18nProvider'
import { TrafonFacade } from '../brand/TrafonFacade'

/**
 * The frame for the error pages: the Trafon facade (lights on or off, the status code on its house
 * number plate), a title, a sentence, the error code to quote if there is one, and what to do next.
 * Uses no router, so it also works when the whole app has crashed.
 */
export function ErrorScene({
  plate,
  lights,
  title,
  body,
  reference,
  children,
}: {
  plate?: string
  lights: 'on' | 'off'
  title: string
  body: string
  reference?: string | null
  children: ReactNode
}) {
  return (
    <div className="mx-auto flex max-w-xl flex-col items-center py-6 text-center sm:py-10">
      <TrafonFacade lights={lights} plate={plate} className="w-full max-w-[28rem]" />
      <h1 className="mt-6 font-display text-[1.75rem] leading-tight font-bold tracking-[-0.02em] text-balance sm:text-[2.125rem]">{title}</h1>
      <p className="mt-2 max-w-md text-pretty text-muted">{body}</p>
      {reference && <ErrorReference value={reference} className="mt-4" />}
      <div className="mt-6 flex flex-wrap justify-center gap-2">{children}</div>
    </div>
  )
}

/** "Error code K7Q2MX", with a button to copy it. */
export function ErrorReference({ value, className }: { value: string; className?: string }) {
  const e = useI18n().t.errorPage
  const [copied, setCopied] = useState(false)
  useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])
  return (
    <p className={`inline-flex items-center gap-2 rounded-control border border-line bg-surface py-1 pr-1 pl-3 text-sm ${className ?? ''}`}>
      <span className="text-muted">{e.reference}</span>
      <span className="font-display font-semibold tracking-[0.08em] tabular-nums">{value}</span>
      <button
        type="button"
        className="rounded-[7px] p-1.5 text-muted transition-colors hover:bg-raised hover:text-ink"
        aria-label={copied ? e.copied : e.copy}
        title={copied ? e.copied : e.copy}
        onClick={() =>
          void navigator.clipboard?.writeText(value).then(
            () => setCopied(true),
            () => {},
          )
        }
      >
        {copied ? <Check aria-hidden className="size-4 text-win" /> : <Copy aria-hidden className="size-4" />}
      </button>
    </p>
  )
}
