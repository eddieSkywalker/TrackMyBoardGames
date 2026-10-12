import type { ReactNode } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'
import { useTheme } from '../hooks/useTheme'

// Shared page frame for signed-in screens: themed background plus the DaisyUI navbar (title, Add A Play, theme toggle, profile menu).
function AppLayout({ children }: { children: ReactNode })
{
    const { user, logout } = useAuth0()
    const [theme, setTheme] = useTheme()

    // The avatar shows the first letter of the user's name (or email), for example 'E' for Elenor.
    const userInitial = (user?.name ?? user?.email ?? '?').charAt(0).toUpperCase()

    return (
        <main data-theme={theme} className="min-h-screen w-full bg-base-200 px-4 py-5 text-base-content sm:px-8 sm:py-8">
            <header className="navbar mx-auto max-w-6xl border-b border-base-300 px-0 pb-4">
                {/* Left: the app title, which takes the user back to the collection (the home page for now). */}
                <div className="navbar-start min-w-0">
                    <Link className="btn btn-ghost px-2 text-base font-bold text-primary sm:text-lg" to="/collection">
                        Track My Board Games
                    </Link>
                </div>

                {/* Right: action button, dark mode toggle, and the profile menu. */}
                <div className="navbar-end gap-1 sm:gap-2">
                    <Link className="btn btn-primary btn-sm" to="/scan">Add A Play!</Link>
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

                    {/* Profile menu: a round avatar showing the first letter of the user's name; clicking it opens the dropdown. */}
                    <div className="dropdown dropdown-end">
                        <div className="btn btn-ghost btn-circle avatar avatar-placeholder" tabIndex={0} role="button" aria-label="Open profile menu">
                            <div className="w-9 rounded-full bg-primary text-primary-content">
                                <span className="text-base font-semibold">{userInitial}</span>
                            </div>
                        </div>
                        <ul className="menu dropdown-content z-10 mt-3 w-52 rounded-box bg-base-100 p-2 shadow" tabIndex={0}>
                            {/* There is no profile page yet, so this item is a disabled placeholder. */}
                            <li className="menu-disabled"><span>Profile</span></li>
                            <li>
                                <button
                                    type="button"
                                    onClick={() => logout({ logoutParams: { returnTo: `${window.location.origin}/` } })}
                                >
                                    Sign out
                                </button>
                            </li>
                        </ul>
                    </div>
                </div>
            </header>
            {children}
        </main>
    )
}

export default AppLayout
