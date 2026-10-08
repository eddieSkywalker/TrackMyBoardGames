import type { ReactNode } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'
import { useTheme } from '../hooks/useTheme'

// Shared page frame for signed-in screens: themed background plus the navbar (title, welcome, theme toggle, sign out).
function AppLayout({ children }: { children: ReactNode })
{
    const { user, logout } = useAuth0()
    const [theme, setTheme] = useTheme()

    return (
        <main data-theme={theme} className="min-h-screen w-full bg-base-200 px-4 py-5 text-base-content sm:px-8 sm:py-8">
            <header className="mx-auto flex max-w-6xl items-center justify-between border-b border-base-300 pb-4">
                <Link className="flex items-center gap-3 text-sm font-bold text-primary no-underline" to="/collection">
                    <span className="grid size-9 place-items-center rounded border border-base-300 font-serif text-sm">TB</span>
                    <span>Track My Board Games</span>
                </Link>
                <div className="flex items-center gap-3">
                    <p className="hidden text-sm text-base-content/70 sm:block">Welcome, {user?.name ?? user?.email ?? 'player'}</p>
                    <label className="swap swap-rotate btn btn-ghost btn-sm btn-circle text-primary" title="Toggle dark mode">
                        <input
                            type="checkbox"
                            aria-label="Dark mode"
                            checked={theme === 'boardgame-dark'}
                            onChange={event => setTheme(event.target.checked ? 'boardgame-dark' : 'boardgame-light')}
                        />
                        <svg className="swap-on size-5 fill-current" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M9 21a1 1 0 0 0 1 1h4a1 1 0 0 0 1-1v-1H9v1Zm3-19a7 7 0 0 0-4 12.74V17a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1v-2.26A7 7 0 0 0 12 2Z" />
                        </svg>
                        <svg className="swap-off size-5 fill-none stroke-current" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                            <path d="M9 21h6M10 18h4m-5.5-3.5a7 7 0 1 1 7 0c-.9.7-1.5 1.5-1.5 2.5h-4c0-1-.6-1.8-1.5-2.5Z" />
                        </svg>
                    </label>
                    <button
                        className="btn btn-ghost btn-sm text-secondary"
                        type="button"
                        onClick={() => logout({ logoutParams: { returnTo: `${window.location.origin}/` } })}
                    >
                        Sign out
                    </button>
                </div>
            </header>
            {children}
        </main>
    )
}

export default AppLayout
