import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'

type Tone = 'success' | 'error'
interface ToastItem {
  id: number
  tone: Tone
  message: string
}

const ToastContext = createContext<(message: string, tone?: Tone) => void>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const push = useCallback((message: string, tone: Tone = 'success') => {
    const id = Date.now() + Math.random()
    setItems((xs) => [...xs, { id, tone, message }])
    setTimeout(() => setItems((xs) => xs.filter((x) => x.id !== id)), 4200)
  }, [])

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-24 z-50 flex flex-col items-center gap-2 px-4 md:bottom-6"
      >
        {items.map((t) => (
          <div
            key={t.id}
            className="rise pointer-events-auto flex max-w-md items-center gap-2.5 rounded-control bg-roof px-4 py-3 text-[0.9375rem] text-trim shadow-[0_12px_32px_-12px_rgb(0_0_0/0.45)]"
          >
            {t.tone === 'success' ? (
              <CheckCircle2 aria-hidden className="size-4.5 shrink-0 text-[#7fd1a6]" />
            ) : (
              <XCircle aria-hidden className="size-4.5 shrink-0 text-[#f19a8f]" />
            )}
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const useToast = () => useContext(ToastContext)
