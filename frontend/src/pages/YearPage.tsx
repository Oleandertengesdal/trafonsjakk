import { Link, NavLink, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, X } from 'lucide-react'
import { yearApi } from '../api/endpoints'
import type { GroupYear, PlayerYear } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { useGroupSlides } from '../components/year/groupSlides'
import { usePlayerSlides } from '../components/year/playerSlides'
import { StoryViewer } from '../components/year/StoryViewer'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { Skeleton } from '../components/ui/Skeleton'
import { useI18n } from '../i18n/I18nProvider'
import { cn } from '../lib/cn'

const EARLIEST = 2000

/** "Årets sjakk": /year/2026 is the group's year, /year/2026/kari one player's. */
export function YearPage() {
  const params = useParams()
  const { me } = useAuth()
  const { t } = useI18n()
  const y = t.year
  const thisYear = new Date().getFullYear()
  const year = Number(params.year)
  const valid = Number.isInteger(year) && year >= EARLIEST && year <= thisYear
  const username = params.username
  const mine = username?.toLowerCase() === me!.username.toLowerCase()

  const player = useQuery({
    queryKey: ['year', year, 'player', username?.toLowerCase()],
    queryFn: () => yearApi.player(year, username!),
    enabled: valid && !!username,
  })
  const group = useQuery({ queryKey: ['year', year, 'group'], queryFn: () => yearApi.group(year), enabled: valid && !username })
  const q = username ? player : group

  const tab = (to: string, label: string, active: boolean) => (
    <NavLink
      to={to}
      aria-current={active ? 'page' : undefined}
      className={cn('rounded-full px-3.5 py-1.5 font-medium transition-colors', active ? 'bg-ink text-page' : 'text-muted hover:text-ink')}
    >
      {label}
    </NavLink>
  )
  const base = (yr: number) => (username ? `/year/${yr}/${username}` : `/year/${yr}`)

  return (
    // On phones the story takes the whole screen, like stories elsewhere; from `sm` it sits in the page.
    <div className="fixed inset-0 z-50 flex flex-col items-center gap-2 overflow-y-auto bg-page px-4 pt-[max(env(safe-area-inset-top),0.5rem)] pb-4 [--story-chrome:8.5rem] sm:static sm:z-auto sm:gap-5 sm:overflow-visible sm:bg-transparent sm:p-0 sm:[--story-chrome:19rem]">
      <header className="flex w-full max-w-[420px] flex-col items-center gap-3">
        <div className="hidden items-center gap-2 sm:flex">
          <Link
            to={base(year - 1)}
            aria-label={String(year - 1)}
            className={cn('grid size-8 place-items-center rounded-md text-muted hover:bg-raised hover:text-ink', year - 1 < EARLIEST && 'invisible')}
          >
            <ChevronLeft aria-hidden className="size-4.5" />
          </Link>
          <h1 className="font-display text-[1.5rem] font-bold tracking-[-0.02em]">{y.title(year)}</h1>
          <Link
            to={base(year + 1)}
            aria-label={String(year + 1)}
            className={cn('grid size-8 place-items-center rounded-md text-muted hover:bg-raised hover:text-ink', year + 1 > thisYear && 'invisible')}
          >
            <ChevronRight aria-hidden className="size-4.5" />
          </Link>
        </div>
        <div className="flex w-full items-center justify-between sm:justify-center">
          <Link to={username ? `/players/${username}` : '/'} aria-label={y.close} className="-ml-2 grid size-10 place-items-center rounded-md text-muted hover:text-ink sm:hidden">
            <X aria-hidden className="size-5" />
          </Link>
          <nav aria-label={y.story} className="flex rounded-full border border-line bg-surface p-1 text-[0.8125rem] sm:text-sm">
            {tab(`/year/${year}/${me!.username}`, y.yourYear, mine)}
            {tab(`/year/${year}`, y.groupYear, !username)}
          </nav>
          <span aria-hidden className="size-10 sm:hidden" />
        </div>
        {username && !mine && player.data && (
          <p className="hidden text-sm text-muted sm:block">
            <Link to={`/players/${player.data.player.username}`} className="hover:text-accent">
              {player.data.player.realName}
            </Link>
          </p>
        )}
      </header>

      {!valid ? (
        <Panel className="w-full max-w-[420px]">
          <ErrorNotice error={null} />
        </Panel>
      ) : q.isPending ? (
        <Skeleton className="aspect-[9/16] w-full max-w-[380px] rounded-t-[50%] rounded-b-[18px]" />
      ) : q.isError ? (
        <Panel className="w-full max-w-[420px]">
          <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
        </Panel>
      ) : username ? (
        <PlayerStory key={`${year}-${username}`} data={player.data!} mine={mine} />
      ) : (
        <GroupStory key={year} data={group.data!} />
      )}

      {q.data && (
        <p className="hidden text-sm sm:block">
          {username ? (
            <Link to={`/year/${year}`} className="text-muted hover:text-accent">
              {y.seeGroup}
            </Link>
          ) : (
            <Link to={`/year/${year}/${me!.username}`} className="text-muted hover:text-accent">
              {y.seeYours}
            </Link>
          )}
        </p>
      )}
    </div>
  )
}

function PlayerStory({ data, mine }: { data: PlayerYear; mine: boolean }) {
  const slides = usePlayerSlides(data, mine)
  return <StoryViewer slides={slides} fileStem={`trafonsjakk-${data.year}-${data.player.username}`} />
}

function GroupStory({ data }: { data: GroupYear }) {
  const slides = useGroupSlides(data)
  return <StoryViewer slides={slides} fileStem={`trafonsjakk-${data.year}`} />
}
