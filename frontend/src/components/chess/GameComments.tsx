import { useState } from 'react'
import { Link } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { socialApi } from '../../api/endpoints'
import type { Game, GameComment } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { Button } from '../ui/Button'
import { ErrorNotice } from '../ui/ErrorNotice'
import { Textarea } from '../ui/Field'
import { Skeleton } from '../ui/Skeleton'
import { useToast } from '../ui/Toast'
import { Avatar } from './Avatar'

const MAX = 1000

/** Comments on a game: everyone can read and write; you edit your own, admins can delete any. */
export function GameComments({ game }: { game: Game }) {
  const { t } = useI18n()
  const s = t.social
  const qc = useQueryClient()
  const toast = useToast()
  const errorText = useErrorText()
  const key = ['games', 'comments', game.id]
  const list = useQuery({ queryKey: key, queryFn: () => socialApi.comments(game.id) })
  const [draft, setDraft] = useState('')

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: key })
    void qc.invalidateQueries({ queryKey: ['games'], refetchType: 'none' })
  }
  const post = useMutation({
    mutationFn: (body: string) => socialApi.addComment(game.id, body),
    onSuccess: () => {
      setDraft('')
      refresh()
    },
    onError: (e) => toast(errorText(e), 'error'),
  })

  const canWrite = game.status !== 'VOIDED'

  return (
    <div className="flex flex-col gap-4 px-5 pt-2 pb-5">
      {list.isPending ? (
        <Skeleton className="h-16 w-full" />
      ) : list.isError ? (
        <ErrorNotice error={list.error} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <p className="text-[0.9375rem] text-muted">{s.noComments}</p>
      ) : (
        <ul className="flex flex-col gap-4">
          {list.data.map((c) => (
            <CommentItem key={c.id} gameId={game.id} comment={c} onChanged={refresh} />
          ))}
        </ul>
      )}

      {canWrite ? (
        <form
          className="flex flex-col gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            const body = draft.trim()
            if (body) post.mutate(body)
          }}
        >
          <label htmlFor={`comment-${game.id}`} className="sr-only">
            {s.write}
          </label>
          <Textarea
            id={`comment-${game.id}`}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            maxLength={MAX}
            rows={2}
            placeholder={s.write}
            className="py-2"
          />
          <Button type="submit" size="sm" className="self-end" disabled={!draft.trim()} loading={post.isPending}>
            {s.post}
          </Button>
        </form>
      ) : (
        <p className="text-sm text-muted">{s.voided}</p>
      )}
    </div>
  )
}

function CommentItem({ gameId, comment: c, onChanged }: { gameId: number; comment: GameComment; onChanged: () => void }) {
  const { t, f } = useI18n()
  const s = t.social
  const toast = useToast()
  const errorText = useErrorText()
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(c.body)
  const [confirming, setConfirming] = useState(false)

  const save = useMutation({
    mutationFn: () => socialApi.editComment(gameId, c.id, text.trim()),
    onSuccess: () => {
      setEditing(false)
      onChanged()
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  const remove = useMutation({
    mutationFn: () => socialApi.deleteComment(gameId, c.id),
    onSuccess: onChanged,
    onError: (e) => toast(errorText(e), 'error'),
  })

  return (
    <li className="flex gap-3">
      <Link to={`/players/${c.author.username}`} className="shrink-0">
        <Avatar name={c.author.realName} seed={c.author.username} size="sm" />
      </Link>
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-baseline gap-x-2 text-sm">
          <Link to={`/players/${c.author.username}`} className="font-medium hover:text-accent">
            {c.author.realName}
          </Link>
          <span className="text-faint">
            {f.relative(c.createdAt)}
            {c.editedAt && ` (${s.edited})`}
          </span>
        </p>
        {editing ? (
          <div className="mt-1.5 flex flex-col gap-2">
            <Textarea value={text} onChange={(e) => setText(e.target.value)} maxLength={MAX} rows={2} className="py-2" aria-label={s.edit} />
            <div className="flex gap-2 self-end">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setEditing(false)
                  setText(c.body)
                }}
              >
                {s.cancel}
              </Button>
              <Button size="sm" disabled={!text.trim()} loading={save.isPending} onClick={() => save.mutate()}>
                {s.save}
              </Button>
            </div>
          </div>
        ) : (
          <p className="mt-0.5 text-[0.9375rem] break-words whitespace-pre-wrap">{c.body}</p>
        )}
        {!editing && (c.canEdit || c.canDelete) && (
          <div className="mt-1 flex gap-1">
            {c.canEdit && (
              <Button variant="ghost" size="sm" className="h-7 px-2" onClick={() => setEditing(true)}>
                {s.edit}
              </Button>
            )}
            {c.canDelete &&
              (confirming ? (
                <>
                  <Button variant="danger" size="sm" className="h-7 px-2" loading={remove.isPending} onClick={() => remove.mutate()}>
                    {s.confirmDelete}
                  </Button>
                  <Button variant="ghost" size="sm" className="h-7 px-2" onClick={() => setConfirming(false)}>
                    {s.cancel}
                  </Button>
                </>
              ) : (
                <Button variant="ghost" size="sm" className="h-7 px-2" onClick={() => setConfirming(true)}>
                  {s.delete}
                </Button>
              ))}
          </div>
        )}
      </div>
    </li>
  )
}
