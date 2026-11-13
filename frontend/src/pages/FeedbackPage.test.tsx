import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { en } from '../i18n/en'
import { I18nProvider } from '../i18n/I18nProvider'
import { nb } from '../i18n/nb'
import { SUPPORT_EMAIL } from '../lib/contact'
import { FeedbackPage } from './FeedbackPage'

// No AuthProvider on purpose: the page must work for people who can't log in.
function renderPage(url = '/feedback', lang: 'nb' | 'en' = 'en') {
  localStorage.setItem('tf-lang', lang)
  render(
    <I18nProvider>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/feedback" element={<FeedbackPage />} />
          <Route path="/" element={<p>home</p>} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  )
}

function mailto() {
  const href = screen.getByRole('link', { name: en.feedback.write }).getAttribute('href')!
  const url = new URL(href)
  return { url, subject: url.searchParams.get('subject'), body: url.searchParams.get('body') ?? '' }
}

describe('FeedbackPage', () => {
  it('opens an email to the admin with a subject and the page the problem was on', () => {
    renderPage('/feedback?from=%2Fgames%2F12')
    const { url, subject, body } = mailto()
    expect(url.protocol).toBe('mailto:')
    expect(url.pathname).toBe(SUPPORT_EMAIL)
    expect(subject).toBe(en.feedback.subject)
    expect(body).toContain(`${window.location.origin}/games/12`)
    expect(body).toContain(en.feedback.bodyWhat)
    // Spaces must be %20 in mailto links (a "+" would show up literally in some mail apps)
    expect(screen.getByRole('link', { name: en.feedback.write }).getAttribute('href')).not.toContain('+')
  })

  it('only mentions pages on this site', () => {
    renderPage('/feedback?from=https%3A%2F%2Fevil.example')
    expect(mailto().body).not.toContain('evil.example')
  })

  it('shows the address and copies it', async () => {
    const user = userEvent.setup()
    renderPage()
    expect(screen.getByText(SUPPORT_EMAIL)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: en.feedback.copy }))
    expect(await navigator.clipboard.readText()).toBe(SUPPORT_EMAIL)
    expect(screen.getByRole('button', { name: en.feedback.copied })).toBeInTheDocument()
  })

  it('is in Norwegian by default and links back to the app', () => {
    localStorage.clear()
    render(
      <I18nProvider>
        <MemoryRouter initialEntries={['/feedback']}>
          <FeedbackPage />
        </MemoryRouter>
      </I18nProvider>,
    )
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(nb.feedback.title)
    expect(screen.getByRole('link', { name: nb.feedback.back })).toHaveAttribute('href', '/')
  })

  it('still works when copying is not allowed', async () => {
    const user = userEvent.setup()
    renderPage()
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'))
    await user.click(screen.getByRole('button', { name: en.feedback.copy }))
    expect(screen.getByRole('button', { name: en.feedback.copy })).toBeInTheDocument()
    expect(screen.getByText(SUPPORT_EMAIL)).toBeInTheDocument()
  })

  it('carries the error code from an error page into the email', () => {
    renderPage('/feedback?from=%2Fgames%2F12&ref=K7Q2MX')
    expect(mailto().body).toContain(en.feedback.bodyRef('K7Q2MX'))
    expect(screen.getByText('K7Q2MX')).toBeInTheDocument()
    expect(screen.getByText(en.feedback.refHint)).toBeInTheDocument()
  })

  it('ignores an error code that the app could not have made', () => {
    renderPage('/feedback?ref=%3Cscript%3E')
    expect(mailto().body).not.toContain('script')
    expect(screen.queryByText(en.feedback.refHint)).not.toBeInTheDocument()
  })
})
