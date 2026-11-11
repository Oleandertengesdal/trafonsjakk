import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { adminApi } from '../../api/endpoints'
import { en } from '../../i18n/en'
import { I18nProvider } from '../../i18n/I18nProvider'
import { ToastProvider } from '../ui/Toast'
import { ResetLinkButton } from './ResetLinkButton'

vi.mock('../../api/endpoints', () => ({ adminApi: { passwordReset: vi.fn() } }))

const a = en.admin

describe('ResetLinkButton', () => {
  it('makes a one-time link and lets the admin copy it', async () => {
    const user = userEvent.setup()
    vi.mocked(adminApi.passwordReset).mockResolvedValue({ token: 'tok_en-123', expiresAt: new Date(Date.now() + 86_400_000).toISOString() })
    localStorage.setItem('tf-lang', 'en')
    render(
      <QueryClientProvider client={new QueryClient()}>
        <I18nProvider>
          <ToastProvider>
            <ResetLinkButton user={{ id: 7, realName: 'Ola Hansen' }} />
          </ToastProvider>
        </I18nProvider>
      </QueryClientProvider>,
    )
    await user.click(screen.getByRole('button', { name: a.resetPassword }))
    expect(adminApi.passwordReset).toHaveBeenCalledWith(7)
    const link = `${window.location.origin}/reset-password?token=tok_en-123`
    expect(await screen.findByText(a.resetTitle('Ola Hansen'))).toBeInTheDocument()
    expect(screen.getByText(link)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: a.copyLink }))
    expect(await navigator.clipboard.readText()).toBe(link)
  })
})
