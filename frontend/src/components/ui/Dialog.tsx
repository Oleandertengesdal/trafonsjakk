import { useEffect, useRef, type ReactNode } from 'react'

/** Native <dialog> with focus trapping and Esc handled by the browser. */
export function Dialog({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) d.showModal()
    if (!open && d.open) d.close()
  }, [open])
  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className="m-auto w-[min(92vw,30rem)] rounded-panel border border-line bg-surface p-0 text-ink backdrop:bg-black/45"
    >
      <div className="p-6">
        <h2 className="font-display text-xl font-semibold tracking-[-0.01em]">{title}</h2>
        <div className="mt-4">{children}</div>
      </div>
    </dialog>
  )
}
