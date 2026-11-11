export type Theme = 'light' | 'dark'
const KEY = 'tf-theme'
const META = { light: '#F4EEE3', dark: '#17181B' }

export function getTheme(): Theme {
  try {
    return localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

export function applyTheme(t: Theme = getTheme()) {
  document.documentElement.dataset.theme = t
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', META[t])
}

export function setTheme(t: Theme) {
  try {
    localStorage.setItem(KEY, t)
  } catch {
    /* storage unavailable: theme still applies for this visit */
  }
  applyTheme(t)
}
