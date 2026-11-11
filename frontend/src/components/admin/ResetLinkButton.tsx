import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Copy } from 'lucide-react'
import { adminApi } from '../../api/endpoints'
import { useI18n } from '../../i18n/I18nProvider'
import { useErrorText } from '../../i18n/useErrorText'
import { Button } from '../ui/Button'
import { Dialog } from '../ui/Dialog'
import { useToast } from '../ui/Toast'

/** For a member who forgot their password: makes a one-time link the admin sends them. */
export function ResetLinkButton({ user }: { user: { id: number; realName: string } }) {
  const { t, f } = useI18n()
  const a = t.admin
  const toast = useToast()
  const errorText = useErrorText()
  const [link, setLink] = useState<{ url: string; expiresAt: string } | null>(null)

  const create = useMutation({
    mutationFn: () => adminApi.passwordReset(user.id),
    onSuccess: (r) =>
      setLink({ url: `${window.location.origin}/reset-password?token=${encodeURIComponent(r.token)}`, expiresAt: r.expiresAt }),
    onError: (e) => toast(errorText(e), 'error'),
  })

  async function copy() {
    if (!link) return
    try {
      await navigator.clipboard.writeText(link.url)
      toast(t.common.copied)
    } catch {
      toast(t.common.copyFailed, 'error')
    }
  }

  return (
    <>
      <Button size="sm" variant="ghost" loading={create.isPending} onClick={() => create.mutate()}>
        {a.resetPassword}
      </Button>
      <Dialog open={link !== null} onClose={() => setLink(null)} title={a.resetTitle(user.realName)}>
        {link && (
          <div className="flex flex-col gap-4">
            <p className="text-[0.9375rem] text-muted">{a.resetBody(user.realName)}</p>
            <p className="rounded-control border border-line bg-page px-3 py-2.5 font-mono text-[0.8125rem] leading-relaxed break-all select-all">
              {link.url}
            </p>
            <p className="text-[0.8125rem] text-faint">
              {a.resetValidUntil(f.dateTime(link.expiresAt))} {a.resetReplaces}
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" autoFocus onClick={() => void copy()}>
                <Copy aria-hidden className="size-4" /> {a.copyLink}
              </Button>
              <Button onClick={() => setLink(null)}>{a.done}</Button>
            </div>
          </div>
        )}
      </Dialog>
    </>
  )
}
