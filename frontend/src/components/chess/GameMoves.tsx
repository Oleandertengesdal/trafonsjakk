import { lazy, Suspense, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { gamesApi } from '../../api/endpoints'
import type { Game } from '../../api/types'
import { splitMovetext, type MovePair } from '../../chess/movetext'
import { useAuth } from '../../auth/AuthProvider'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { Button } from '../ui/Button'
import { useToast } from '../ui/Toast'

// The board and chess.js load only when someone actually records moves
const MoveRecorder = lazy(() => import('./MoveRecorder'))
const GameViewer = lazy(() => import('./GameViewer'))

/** The moves of a game: a numbered list, and for the players (or an admin) a board to add or fix them. */
export function GameMoves({ game }: { game: Game }) {
  const { t } = useI18n()
  const m = t.moves
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const errorText = useErrorText()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<string | null>(game.moves)

  const save = useMutation({
    mutationFn: () => gamesApi.updateMoves(game.id, draft),
    onSuccess: (updated) => {
      qc.setQueryData(['games', 'detail', game.id], updated)
      setEditing(false)
      toast(m.saved, 'success')
    },
    onError: (e) => toast(errorText(e), 'error'),
  })

  if (editing) {
    return (
      <div className="flex flex-col gap-4 px-5 pt-2 pb-5">
        <Suspense fallback={<p className="text-sm text-muted">{m.loading}</p>}>
          <MoveRecorder
            value={draft}
            onChange={setDraft}
            orientation={me?.id === game.black.id ? 'black' : 'white'}
            result={game.result}
          />
        </Suspense>
        <div className="flex justify-end gap-2">
          <Button
            variant="ghost"
            onClick={() => {
              setEditing(false)
              setDraft(game.moves)
            }}
          >
            {t.social.cancel}
          </Button>
          <Button loading={save.isPending} onClick={() => save.mutate()}>
            {m.save}
          </Button>
        </div>
      </div>
    )
  }

  const rows = game.moves ? splitMovetext(game.moves) : null
  return (
    <div className="flex flex-col gap-3 px-5 pt-2 pb-5">
      {rows ? (
        <Suspense fallback={<MoveList rows={rows} label={m.list} />}>
          <GameViewer
            moves={game.moves!}
            orientation={me?.id === game.black.id ? 'black' : 'white'}
            whiteName={game.white.realName}
            blackName={game.black.realName}
          />
        </Suspense>
      ) : game.moves ? (
        <p className="max-h-64 overflow-auto rounded-control bg-page px-3 py-2.5 text-[0.875rem] leading-relaxed whitespace-pre-wrap">
          {game.moves}
        </p>
      ) : null}
      {game.canEditMoves && (
        <Button variant="secondary" size="sm" className="self-start" onClick={() => setEditing(true)}>
          {game.moves ? m.edit : m.add}
        </Button>
      )}
    </div>
  )
}

/** The plain numbered list, shown while the board viewer loads. */
function MoveList({ rows, label }: { rows: MovePair[]; label: string }) {
  return (
    <ol
      aria-label={label}
      className="grid max-h-80 grid-cols-[auto_1fr_1fr] content-start gap-x-4 gap-y-0.5 overflow-y-auto rounded-control bg-page px-3 py-2.5 font-mono text-sm tabular-nums sm:grid-cols-[auto_1fr_1fr_auto_1fr_1fr]"
    >
      {rows.map((r) => (
        <li key={r.n} className="col-span-3 grid grid-cols-subgrid">
          <span className="text-faint">{r.n}.</span>
          <span>{r.white}</span>
          <span>{r.black ?? ''}</span>
        </li>
      ))}
    </ol>
  )
}
