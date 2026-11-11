import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { gamesApi } from '../api/endpoints'
import { ToastProvider } from '../components/ui/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { pendingGame, player } from '../test/fixtures'
import { GameDetailPage } from './GameDetailPage'

vi.mock('../api/endpoints', () => ({ gamesApi: { get: vi.fn() } }))
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ me: { id: 99, role: 'USER' } }) }))

const PAYLOAD = '<img src=x onerror="window.__pwned=1"><script>window.__pwned=2</script>'

describe('text from other users is shown as text', () => {
  it('never turns names, notes, openings or dispute reasons into HTML', async () => {
    const evil = { ...player(1, PAYLOAD), username: 'evil' }
    vi.mocked(gamesApi.get).mockResolvedValue(
      pendingGame({
        white: evil,
        reportedBy: evil,
        status: 'DISPUTED',
        notes: PAYLOAD,
        opening: PAYLOAD,
        moves: PAYLOAD,
        disputeReason: PAYLOAD,
      }),
    )
    localStorage.setItem('tf-lang', 'en')
    const { container } = render(
      <QueryClientProvider client={new QueryClient()}>
        <I18nProvider>
          <ToastProvider>
            <MemoryRouter initialEntries={['/games/42']}>
              <Routes>
                <Route path="/games/:id" element={<GameDetailPage />} />
              </Routes>
            </MemoryRouter>
          </ToastProvider>
        </I18nProvider>
      </QueryClientProvider>,
    )
    expect((await screen.findAllByText((t) => t.includes('<img src=x'))).length).toBeGreaterThanOrEqual(3)
    expect(container.querySelector('img[src="x"]')).toBeNull()
    expect(container.querySelector('script')).toBeNull()
    expect((window as { __pwned?: number }).__pwned).toBeUndefined()
  })
})
