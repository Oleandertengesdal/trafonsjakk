import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Crown, Plus, Trophy } from 'lucide-react'
import { tournamentsApi } from '../api/endpoints'
import type { TournamentSummary } from '../api/types'
import { PageHeader } from '../components/layout/PageHeader'
import { ButtonLink } from '../components/ui/Button'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { SkeletonRows } from '../components/ui/Skeleton'
import { useI18n } from '../i18n/I18nProvider'
import { cn } from '../lib/cn'

export function TournamentsPage() {
  const { t } = useI18n()
  const q = useQuery({ queryKey: ['tournaments'], queryFn: tournamentsApi.list })
  const newButton = (
    <ButtonLink to="/tournaments/new" className="gap-2">
      <Plus aria-hidden className="size-4.5" /> {t.tournaments.new}
    </ButtonLink>
  )
  const live = q.data?.filter((x) => x.status === 'IN_PROGRESS' || x.status === 'REGISTRATION') ?? []
  const past = q.data?.filter((x) => x.status === 'COMPLETED' || x.status === 'CANCELLED') ?? []

  return (
    <>
      <PageHeader title={t.tournaments.title} description={t.tournaments.description} action={newButton} />
      {q.isPending ? (
        <Panel>
          <SkeletonRows rows={4} />
        </Panel>
      ) : q.isError ? (
        <Panel>
          <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
        </Panel>
      ) : q.data.length === 0 ? (
        <Panel>
          <EmptyState icon={<Trophy />} title={t.tournaments.emptyTitle} action={newButton}>
            {t.tournaments.emptyBody}
          </EmptyState>
        </Panel>
      ) : (
        <div className="flex flex-col gap-8">
          {live.length > 0 && <Section title={t.tournaments.running} items={live} />}
          {past.length > 0 && <Section title={t.tournaments.finished} items={past} />}
        </div>
      )}
    </>
  )
}

function Section({ title, items }: { title: string; items: TournamentSummary[] }) {
  const { t, f } = useI18n()
  return (
    <section>
      <h2 className="mb-3 font-display text-lg font-semibold">{title}</h2>
      <Panel>
        <ul className="divide-y divide-line">
          {items.map((x) => (
            <li key={x.id}>
              <Link to={`/tournaments/${x.id}`} className="flex items-center gap-4 px-5 py-4 transition-colors hover:bg-raised/70">
                <span
                  className={cn(
                    'flex h-11 w-10 shrink-0 items-center justify-center rounded-t-full rounded-b-[5px]',
                    x.status === 'COMPLETED' ? 'bg-brand text-on-accent' : 'bg-raised text-muted',
                  )}
                >
                  {x.status === 'COMPLETED' ? <Crown aria-hidden className="mt-1 size-5" /> : <Trophy aria-hidden className="mt-1 size-5" />}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{x.name}</p>
                  <p className="truncate text-[0.8125rem] text-muted">
                    {t.tournaments.meta(
                      x.doubleRoundRobin ? `${t.format[x.format]} (${t.format.double})` : t.format[x.format],
                      t.common.players(x.participantCount),
                      x.createdBy.realName,
                    )}
                  </p>
                </div>
                <div className="hidden text-right text-[0.8125rem] sm:block">
                  <p className={cn(x.status === 'IN_PROGRESS' ? 'text-win' : x.status === 'REGISTRATION' ? 'text-gold' : 'text-muted')}>
                    {x.winner ? t.tournaments.wonBy(x.winner.realName) : t.tournamentStatus[x.status]}
                  </p>
                  <p className="text-faint">{f.date(x.completedAt ?? x.startedAt ?? x.createdAt)}</p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </Panel>
    </section>
  )
}
