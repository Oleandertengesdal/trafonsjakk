import { ErrorScene } from '../components/errors/ErrorScene'
import { ButtonLink } from '../components/ui/Button'
import { useI18n } from '../i18n/I18nProvider'

export type Missing = 'page' | 'game' | 'player' | 'tournament' | 'live'

/** Where to look instead: the list the missing thing would have been in. */
const LIST: Record<Exclude<Missing, 'page'>, string> = { game: '/games', player: '/leaderboard', tournament: '/tournaments', live: '/live' }

/** 404: an unknown address, or a game, player, tournament or live game that doesn't exist (any more). */
export function NotFoundPage({ what = 'page' }: { what?: Missing }) {
  const e = useI18n().t.errorPage
  const list = what === 'page' ? null : { to: LIST[what], label: e.allOf[what] }
  return (
    <ErrorScene plate="404" lights="on" title={e.notFound[what].title} body={e.notFound[what].body}>
      {list && <ButtonLink to={list.to}>{list.label}</ButtonLink>}
      <ButtonLink to="/" variant={list ? 'secondary' : 'primary'}>
        {e.home}
      </ButtonLink>
    </ErrorScene>
  )
}
