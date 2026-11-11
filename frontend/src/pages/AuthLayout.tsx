import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router'
import { LogoMark, Wordmark } from '../components/brand/LogoMark'
import { TrafonFacade } from '../components/brand/TrafonFacade'
import { LanguageSwitch } from '../components/ui/LanguageSwitch'
import { useI18n } from '../i18n/I18nProvider'

/** Login/register frame: the Trafon facade beside the form. */
export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
  showFeedbackLink = true,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
  showFeedbackLink?: boolean
}) {
  const { t } = useI18n()
  const { pathname } = useLocation()
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
      <div className="relative hidden flex-col justify-between overflow-hidden border-r border-line bg-accent-soft p-12 lg:flex">
        <div className="flex items-center gap-3">
          <LogoMark className="size-9" />
          <Wordmark className="text-2xl" />
        </div>
        <div className="max-w-md">
          <p className="font-display text-[2.75rem] leading-[1.05] font-bold tracking-[-0.03em] text-balance">{t.auth.headline}</p>
          <p className="mt-4 text-lg text-muted">{t.auth.body}</p>
        </div>
        <TrafonFacade className="-mx-12 -mb-12 w-[calc(100%+6rem)] max-w-none" />
      </div>
      <div className="flex flex-col px-5 py-6 sm:px-12">
        <div className="flex justify-end">
          <LanguageSwitch />
        </div>
        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-8">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <LogoMark className="size-8" />
            <Wordmark className="text-xl" />
          </div>
          <h1 className="font-display text-[2rem] font-bold tracking-[-0.025em]">{title}</h1>
          <p className="mt-1.5 text-muted">{subtitle}</p>
          <div className="mt-8">{children}</div>
          <div className="mt-8 text-[0.9375rem] text-muted">{footer}</div>
          {showFeedbackLink && (
            <Link to={`/feedback?from=${encodeURIComponent(pathname)}`} className="mt-3 text-sm text-faint hover:text-muted hover:underline">
              {t.feedback.link}
            </Link>
          )}
        </div>
      </div>
    </div>
  )
}
