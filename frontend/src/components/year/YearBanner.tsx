import { Link } from 'react-router'
import { ArrowRight } from 'lucide-react'
import { useAuth } from '../../auth/AuthProvider'
import { useI18n } from '../../i18n/I18nProvider'
import { reviewYear } from './season'

/** Home page, December and January only: the way into "Årets sjakk". */
export function YearBanner({ now = new Date() }: { now?: Date }) {
  const { me } = useAuth()
  const { t } = useI18n()
  const y = t.year
  const { year, featured } = reviewYear(now)
  if (!featured) return null
  return (
    <section className="relative overflow-hidden rounded-t-[2.5rem] rounded-b-panel bg-[#2e333a] px-6 py-6 text-[#fbf6ec] sm:px-9">
      <div aria-hidden className="pointer-events-none absolute top-3 right-6 hidden h-[calc(100%-1.5rem)] w-28 rounded-t-full border border-[#fbf6ec]/20 sm:block" />
      <h2 className="font-display text-[1.75rem] leading-tight font-bold tracking-[-0.02em]">{y.bannerTitle(year)}</h2>
      <p className="mt-2 max-w-[48ch] text-[0.9375rem] text-[#fbf6ec]/80">{y.bannerBody}</p>
      <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2">
        <Link
          to={`/year/${year}/${me!.username}`}
          className="inline-flex h-10 items-center gap-2 rounded-full bg-[#fbf6ec] px-5 font-medium text-[#23252a] hover:bg-white"
        >
          {y.bannerOpen}
          <ArrowRight aria-hidden className="size-4" />
        </Link>
        <Link to={`/year/${year}`} className="text-[0.9375rem] font-medium underline decoration-[#fbf6ec]/40 underline-offset-4 hover:decoration-[#fbf6ec]">
          {y.seeGroup}
        </Link>
      </div>
    </section>
  )
}
