import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Heart, MessageCircle } from 'lucide-react'
import { socialApi } from '../../api/endpoints'
import type { CommentLikes, Game, GameComment, PlayerRef } from '../../api/types'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { cn } from '../../lib/cn'
import { Button } from '../ui/Button'
import { ErrorNotice } from '../ui/ErrorNotice'
import { Textarea } from '../ui/Field'
import { Skeleton } from '../ui/Skeleton'
import { useToast } from '../ui/Toast'
import { Avatar } from './Avatar'

const MAX = 1000

/** Ctrl/Cmd+Enter submits the form the textarea sits in. */
function sendOnCtrlEnter(e: KeyboardEvent<HTMLTextAreaElement>) {
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    e.currentTarget.form?.requestSubmit()
  }
}

/** Replaces one comment's like state anywhere in the thread tree. */
function withLikes(list: GameComment[], id: number, likes: CommentLikes): GameComment[] {
  return list.map((c) =>
    c.id === id ? { ...c, ...likes } : c.replies.length ? { ...c, replies: withLikes(c.replies, id, likes) } : c,
  )
}

interface ReplyTarget {
  /** The comment being answered; the server files it under the thread's first comment. */
  id: number
  author: PlayerRef | null
}

/**
 * Comments on a game, one level of replies like Facebook. Everyone can read, write and reply;
 * you edit your own, admins can delete any, and you can like everyone's comments but your own.
 */
export function GameComments({ game }: { game: Game }) {
  const { t } = useI18n()
  const s = t.social
  const qc = useQueryClient()
  const toast = useToast()
  const errorText = useErrorText()
  const key = ['games', 'comments', game.id]
  const list = useQuery({ queryKey: key, queryFn: () => socialApi.comments(game.id) })
  const [draft, setDraft] = useState('')
  const [replying, setReplying] = useState<{ thread: number; to: ReplyTarget } | null>(null)

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: key })
    void qc.invalidateQueries({ queryKey: ['games'], refetchType: 'none' })
  }
  const post = useMutation({
    mutationFn: (body: string) => socialApi.addComment(game.id, body, undefined),
    onSuccess: () => {
      setDraft('')
      refresh()
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  const onLikes = (id: number, likes: CommentLikes) => qc.setQueryData<GameComment[]>(key, (old) => old && withLikes(old, id, likes))

  const canWrite = game.status !== 'VOIDED'
  const ctx: ThreadContext = {
    gameId: game.id,
    canWrite,
    onChanged: refresh,
    onLikes,
    onReply: (thread, to) => setReplying({ thread, to }),
  }

  return (
    <div className="flex flex-col gap-5 px-5 pt-2 pb-5">
      {list.isPending ? (
        <Skeleton className="h-16 w-full" />
      ) : list.isError ? (
        <ErrorNotice error={list.error} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <p className="text-[0.9375rem] text-muted">{s.noComments}</p>
      ) : (
        <ul className="flex flex-col gap-5">
          {list.data.map((c) => (
            <li key={c.id}>
              <CommentItem comment={c} thread={c.id} ctx={ctx} />
              {(c.replies.length > 0 || replying?.thread === c.id) && (
                <div className="mt-3 ml-[1.0625rem] border-l-2 border-line pl-[1.5625rem]">
                  {c.replies.length > 0 && (
                    <ul aria-label={s.replies(c.replies.length)} className="flex flex-col gap-3.5">
                      {c.replies.map((r) => (
                        <li key={r.id}>
                          <CommentItem comment={r} thread={c.id} ctx={ctx} small />
                        </li>
                      ))}
                    </ul>
                  )}
                  {replying?.thread === c.id && (
                    <ReplyForm
                      gameId={game.id}
                      to={replying.to}
                      className={c.replies.length > 0 ? 'mt-3' : undefined}
                      onCancel={() => setReplying(null)}
                      onSent={() => {
                        setReplying(null)
                        refresh()
                      }}
                    />
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {canWrite ? (
        <form
          className="flex flex-col gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            const body = draft.trim()
            if (body && !post.isPending) post.mutate(body)
          }}
        >
          <label htmlFor={`comment-${game.id}`} className="sr-only">
            {s.write}
          </label>
          <Textarea
            id={`comment-${game.id}`}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={sendOnCtrlEnter}
            maxLength={MAX}
            rows={2}
            placeholder={s.write}
            className="py-2"
          />
          <div className="flex items-center justify-end gap-3">
            <span className="hidden text-xs text-faint sm:inline">{s.sendHint}</span>
            <Button type="submit" size="sm" disabled={!draft.trim()} loading={post.isPending}>
              {s.post}
            </Button>
          </div>
        </form>
      ) : (
        <p className="text-sm text-muted">{s.voided}</p>
      )}
    </div>
  )
}

interface ThreadContext {
  gameId: number
  canWrite: boolean
  onChanged: () => void
  onLikes: (id: number, likes: CommentLikes) => void
  onReply: (thread: number, to: ReplyTarget) => void
}

function ReplyForm({
  gameId,
  to,
  className,
  onCancel,
  onSent,
}: {
  gameId: number
  to: ReplyTarget
  className?: string
  onCancel: () => void
  onSent: () => void
}) {
  const { t } = useI18n()
  const s = t.social
  const toast = useToast()
  const errorText = useErrorText()
  const [text, setText] = useState('')
  const box = useRef<HTMLTextAreaElement>(null)
  const label = to.author ? s.replyTo(to.author.realName) : s.writeReply

  // Opening a reply (or switching who you answer) puts the cursor in the box.
  useEffect(() => box.current?.focus(), [to.id])

  const send = useMutation({
    mutationFn: (body: string) => socialApi.addComment(gameId, body, to.id),
    onSuccess: onSent,
    onError: (e) => toast(errorText(e), 'error'),
  })

  return (
    <form
      className={cn('flex flex-col gap-2', className)}
      onSubmit={(e) => {
        e.preventDefault()
        const body = text.trim()
        if (body && !send.isPending) send.mutate(body)
      }}
    >
      <Textarea
        ref={box}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') onCancel()
          else sendOnCtrlEnter(e)
        }}
        maxLength={MAX}
        rows={2}
        aria-label={label}
        placeholder={label}
        className="py-2"
      />
      <div className="flex gap-2 self-end">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
          {s.cancel}
        </Button>
        <Button type="submit" size="sm" disabled={!text.trim()} loading={send.isPending}>
          {s.postReply}
        </Button>
      </div>
    </form>
  )
}

function CommentItem({ comment: c, thread, ctx, small }: { comment: GameComment; thread: number; ctx: ThreadContext; small?: boolean }) {
  const { t, f } = useI18n()
  const s = t.social
  const toast = useToast()
  const errorText = useErrorText()
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(c.body)
  const [confirming, setConfirming] = useState(false)

  const save = useMutation({
    mutationFn: () => socialApi.editComment(ctx.gameId, c.id, text.trim()),
    onSuccess: () => {
      setEditing(false)
      ctx.onChanged()
    },
    onError: (e) => toast(errorText(e), 'error'),
  })
  const remove = useMutation({
    mutationFn: () => socialApi.deleteComment(ctx.gameId, c.id),
    onSuccess: ctx.onChanged,
    onError: (e) => toast(errorText(e), 'error'),
  })

  const reply = ctx.canWrite && (
    <ActionButton onClick={() => ctx.onReply(thread, { id: c.id, author: c.author })}>
      <MessageCircle aria-hidden className="size-3.5" />
      {s.reply}
    </ActionButton>
  )

  if (c.deleted || !c.author) {
    return (
      <div className="flex gap-3">
        <span aria-hidden className={cn('shrink-0 rounded-full border border-dashed border-line', small ? 'size-7' : 'size-9')} />
        <div className="min-w-0 flex-1">
          <p className="pt-1.5 text-[0.9375rem] text-muted italic">{s.deleted}</p>
          {reply && <div className="mt-0.5 -ml-2 flex">{reply}</div>}
        </div>
      </div>
    )
  }

  const author = c.author
  return (
    <div className="flex gap-3">
      <Link to={`/players/${author.username}`} className="shrink-0">
        <Avatar name={author.realName} seed={author.username} size="sm" />
      </Link>
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-baseline gap-x-2 text-sm">
          <Link to={`/players/${author.username}`} className="font-medium hover:text-accent">
            {author.realName}
          </Link>
          {c.replyTo && <span className="text-muted">{s.replyingTo(c.replyTo.realName)}</span>}
          <span className="text-faint">
            {f.relative(c.createdAt)}
            {c.editedAt && ` (${s.edited})`}
          </span>
        </p>
        {editing ? (
          <div className="mt-1.5 flex flex-col gap-2">
            <Textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && text.trim()) {
                  e.preventDefault()
                  save.mutate()
                }
              }}
              maxLength={MAX}
              rows={2}
              className="py-2"
              aria-label={s.edit}
            />
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
        {!editing && (
          <div className="mt-1 -ml-2 flex flex-wrap items-center gap-x-0.5">
            <LikeButton comment={c} ctx={ctx} />
            {reply}
            {c.canEdit && <ActionButton onClick={() => setEditing(true)}>{s.edit}</ActionButton>}
            {c.canDelete &&
              (confirming ? (
                <>
                  <Button variant="danger" size="sm" className="h-7 px-2" loading={remove.isPending} onClick={() => remove.mutate()}>
                    {s.confirmDelete}
                  </Button>
                  <ActionButton onClick={() => setConfirming(false)}>{s.cancel}</ActionButton>
                </>
              ) : (
                <ActionButton onClick={() => setConfirming(true)}>{s.delete}</ActionButton>
              ))}
          </div>
        )}
      </div>
    </div>
  )
}

function ActionButton({ className, active, ...props }: React.ComponentProps<'button'> & { active?: boolean }) {
  return (
    <button
      type="button"
      className={cn(
        'inline-flex h-7 items-center gap-1.5 rounded-md px-2 text-[0.8125rem] font-medium transition-colors hover:bg-raised disabled:opacity-60',
        active ? 'text-accent' : 'text-muted hover:text-ink',
        className,
      )}
      {...props}
    />
  )
}

function LikeButton({ comment: c, ctx }: { comment: GameComment; ctx: ThreadContext }) {
  const { t } = useI18n()
  const s = t.social
  const toast = useToast()
  const errorText = useErrorText()
  const names = c.likedBy.map((p) => p.realName).join(', ')
  const title = names ? s.likedBy(names) : undefined

  const toggle = useMutation({
    mutationFn: () => (c.likedByMe ? socialApi.unlikeComment(ctx.gameId, c.id) : socialApi.likeComment(ctx.gameId, c.id)),
    onSuccess: (likes) => ctx.onLikes(c.id, likes),
    onError: (e) => toast(errorText(e), 'error'),
  })

  if (!c.canLike) {
    if (c.likeCount === 0) return null
    return (
      <span title={title} className="inline-flex h-7 items-center gap-1.5 px-2 text-[0.8125rem] font-medium text-muted tabular-nums">
        <Heart aria-hidden className="size-3.5 fill-current text-accent" />
        <span aria-hidden>{c.likeCount}</span>
        <span className="sr-only">{s.likes(c.likeCount)}</span>
      </span>
    )
  }

  return (
    <ActionButton
      aria-pressed={c.likedByMe}
      aria-label={c.likedByMe ? s.unlike : s.like}
      title={title}
      disabled={toggle.isPending}
      onClick={() => toggle.mutate()}
      active={c.likedByMe}
      className="tabular-nums"
    >
      <Heart aria-hidden className={cn('size-3.5', c.likedByMe && 'fill-current')} />
      {c.likeCount > 0 ? c.likeCount : s.like}
    </ActionButton>
  )
}
