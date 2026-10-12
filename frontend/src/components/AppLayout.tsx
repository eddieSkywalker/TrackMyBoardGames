import type { ReactNode } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link, useNavigate } from 'react-router-dom'
import { useTheme } from '../hooks/useTheme'

// Shared page frame for signed-in screens: themed background plus the DaisyUI navbar (title, Add A Play, theme toggle, profile menu).
function AppLayout({ children }: { children: ReactNode })
{
    const { user, logout } = useAuth0()
    const [theme, setTheme] = useTheme()
    const navigate = useNavigate()

    // Close the open dropdown (DaisyUI dropdowns stay open while they hold focus), then go to the collection page
    // and tell it what to do through the router state.
    function runCollectionAction(action: 'add-game' | 'shelf-add' | 'shelf-remove')
    {
        if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
        navigate('/collection', { state: { action } })
    }

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
                    {/* Create menu: a plus icon that opens Add A Play, Add A Game, and the nested Shelf Background options. */}
                    <div className="dropdown dropdown-end">
                        <div className="btn btn-primary btn-sm btn-circle" tabIndex={0} role="button" aria-label="Open create menu">
                            <svg className="size-5 fill-none stroke-current" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" strokeWidth="2.5" strokeLinecap="round" aria-hidden="true">
                                <path d="M12 5v14M5 12h14" />
                            </svg>
                        </div>
                        <ul className="menu dropdown-content z-10 mt-3 w-56 rounded-box bg-base-100 p-2 shadow" tabIndex={0}>
                            <li><Link to="/scan" onClick={() => (document.activeElement as HTMLElement | null)?.blur()}>Add A Play!</Link></li>
                            <li><button type="button" onClick={() => runCollectionAction('add-game')}>Add A Game!</button></li>
                            <li>
                                <details>
                                    <summary>Shelf Background</summary>
                                    <ul>
                                        <li><button type="button" onClick={() => runCollectionAction('shelf-add')}>Add Shelf Background!</button></li>
                                        <li><button type="button" onClick={() => runCollectionAction('shelf-remove')}>Remove Shelf Background</button></li>
                                    </ul>
                                </details>
                            </li>
                        </ul>
                    </div>
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
