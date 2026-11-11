import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'

// jsdom has no modal dialogs; enough of <dialog> for our Dialog component.
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }
}

// jsdom has no layout; the chess board only needs the observer to exist.
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
}

export function clearCookies() {
  for (const c of document.cookie.split('; ')) {
    const name = c.split('=')[0]
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
}

beforeEach(() => {
  clearCookies()
  try {
    localStorage.clear()
  } catch {
    /* ignore */
  }
})

afterEach(() => cleanup())
