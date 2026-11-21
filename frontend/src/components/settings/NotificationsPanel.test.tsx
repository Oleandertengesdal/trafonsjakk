import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { pushApi } from '../../api/endpoints'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import { pushState, turnOff, turnOn } from '../../lib/push'
import { ToastProvider } from '../ui/Toast'
import { NotificationsPanel } from './NotificationsPanel'

vi.mock('../../lib/push', () => ({ pushState: vi.fn(), turnOn: vi.fn(), turnOff: vi.fn() }))
vi.mock('../../api/endpoints', () => ({ pushApi: { test: vi.fn() } }))

const n = en.notifications

function setup() {
  localStorage.setItem('tf-lang', 'en')
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <I18nProvider>
        <ToastProvider>
          <NotificationsPanel />
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  )
}

describe('NotificationsPanel', () => {
  beforeEach(() => {
    vi.mocked(pushState).mockReset()
    vi.mocked(turnOn).mockReset()
    vi.mocked(turnOff).mockReset()
    vi.mocked(pushApi.test).mockReset()
  })

  it('turns notifications on, sends a test, and turns them off', async () => {
    vi.mocked(pushState).mockResolvedValue('off')
    vi.mocked(turnOn).mockResolvedValue('on')
    vi.mocked(turnOff).mockResolvedValue('off')
    vi.mocked(pushApi.test).mockResolvedValue({ sent: 2 })
    setup()
    await userEvent.click(await screen.findByRole('button', { name: n.turnOn }))
    expect(turnOn).toHaveBeenCalled()
    expect(await screen.findByText(n.onBody)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: n.test }))
    expect(await screen.findByText(n.testSent(2))).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: n.turnOff }))
    expect(await screen.findByText(n.offBody)).toBeInTheDocument()
  })

  it('explains why it can’t be turned on', async () => {
    vi.mocked(pushState).mockResolvedValue('install-first')
    setup()
    expect(await screen.findByText(n.why['install-first'])).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('says it’s blocked when the browser said no', async () => {
    vi.mocked(pushState).mockResolvedValue('off')
    vi.mocked(turnOn).mockResolvedValue('blocked')
    setup()
    await userEvent.click(await screen.findByRole('button', { name: n.turnOn }))
    expect(await screen.findByText(n.why.blocked)).toBeInTheDocument()
  })

  it('says so when the browser can’t subscribe', async () => {
    vi.mocked(pushState).mockResolvedValue('off')
    vi.mocked(turnOn).mockRejectedValue(new DOMException('push service error', 'AbortError'))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: n.turnOn }))
    expect(await screen.findByText(n.failed)).toBeInTheDocument()
  })
})
