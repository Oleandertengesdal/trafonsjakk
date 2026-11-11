import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { TrendingUp } from 'lucide-react'
import { leaderboardApi } from '../api/endpoints'
import { Avatar } from '../components/chess/Avatar'
import { Rating } from '../components/chess/PlayerLink'
import { PageHeader } from '../components/layout/PageHeader'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorNotice } from '../components/ui/ErrorNotice'
import { Panel } from '../components/ui/Panel'
import { SkeletonRows } from '../components/ui/Skeleton'
import { signed } from '../i18n/format'
import { useI18n } from '../i18n/I18nProvider'
import { cn } from '../lib/cn'

export function LeaderboardPage() {
  const { t, f } = useI18n()
  const lb = t.leaderboard
  const q = useQuery({ queryKey: ['leaderboard'], queryFn: leaderboardApi.get })

  return (
    <>
      <PageHeader title={lb.title} description={lb.description} />
      <Panel>
        {q.isPending ? (
          <SkeletonRows rows={8} />
        ) : q.isError ? (
          <ErrorNotice error={q.error} onRetry={() => q.refetch()} />
        ) : q.data.length === 0 ? (
          <EmptyState icon={<TrendingUp />} title={lb.empty} />
        ) : (
          <table className="w-full text-[0.9375rem]">
            <caption className="sr-only">{lb.caption}</caption>
            <thead>
              <tr className="text-left text-[0.8125rem] text-muted">
                <th scope="col" className="w-12 px-5 py-3 font-medium">#</th>
                <th scope="col" className="py-3 font-medium">{lb.player}</th>
                <th scope="col" className="px-3 py-3 text-right font-medium">{lb.rating}</th>
                <th scope="col" className="hidden px-3 py-3 text-right font-medium sm:table-cell" title={lb.last5Title}>{lb.last5}</th>
                <th scope="col" className="hidden px-3 py-3 text-right font-medium md:table-cell">{lb.wdl}</th>
                <th scope="col" className="hidden px-3 py-3 text-right font-medium md:table-cell">{lb.score}</th>
                <th scope="col" className="hidden px-5 py-3 text-right font-medium lg:table-cell">{lb.lastGame}</th>
              </tr>
            </thead>
            <tbody>
              {q.data.map((r, i) => {
                const firstProvisional = r.rank === null && i > 0 && q.data[i - 1].rank !== null
                const score = r.games ? (r.wins + r.draws / 2) / r.games : 0
                return (
                  <tr key={r.player.id} className={cn('border-t border-line', firstProvisional && 'border-t-2 border-t-faint/40')}>
                    <td className={cn('px-5 py-3 font-display font-semibold tabular-nums', r.rank === 1 ? 'text-accent' : 'text-faint')}>{r.rank ?? ''}</td>
                    <td className="py-3">
                      <Link to={`/players/${r.player.username}`} className="flex items-center gap-3 hover:text-accent">
                        <Avatar name={r.player.realName} seed={r.player.username} size="sm" />
                        <span className="min-w-0">
                          <span className="block truncate font-medium">{r.player.realName}</span>
                          <span className="block truncate text-[0.8125rem] text-muted">{r.player.username}</span>
                        </span>
                      </Link>
                    </td>
                    <td className="px-3 py-3 text-right">
                      <Rating value={r.player.rating} provisional={r.player.provisional} className="font-display text-lg font-semibold" />
                      <span className="block text-[0.75rem] text-faint">±{r.player.rd}</span>
                    </td>
                    <td className={cn('hidden px-3 py-3 text-right tabular-nums sm:table-cell', r.trend > 0 ? 'text-win' : r.trend < 0 ? 'text-loss' : 'text-faint')}>
                      {r.games ? signed(r.trend) : ''}
                    </td>
                    <td className="hidden px-3 py-3 text-right text-muted tabular-nums md:table-cell">
                      {r.wins} / {r.draws} / {r.losses}
                    </td>
                    <td className="hidden px-3 py-3 text-right tabular-nums md:table-cell">{r.games ? f.pct(score) : ''}</td>
                    <td className="hidden px-5 py-3 text-right text-muted lg:table-cell">{r.lastPlayedAt ? f.relative(r.lastPlayedAt) : t.common.never}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </Panel>
    </>
  )
}
