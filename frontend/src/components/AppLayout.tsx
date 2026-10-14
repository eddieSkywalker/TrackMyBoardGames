import type { ReactNode } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useTheme } from '../hooks/useTheme'

// Shared page frame with a navbar and a second set of navigation controls in the mobile dock.
function AppLayout({ children }: { children: ReactNode })
{
    const { user, logout } = useAuth0()
    const [theme, setTheme] = useTheme()
    const navigate = useNavigate()

    function closeMenu()
    {
        if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
    }

    // Both navigation bars use the same collection actions and menu items.
    function runCollectionAction(action: 'add-game' | 'shelf-add' | 'shelf-remove')
    {
        closeMenu()
        navigate('/collection', { state: { action } })
    }

    // The avatar shows the first letter of the user's name (or email), for example 'E' for Elenor.
    const userInitial = (user?.name ?? user?.email ?? '?').charAt(0).toUpperCase()

    function createMenuItems()
    {
        return (
            <>
                <li><Link to="/scan" onClick={closeMenu}>Add A Play!</Link></li>
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
            </>
        )
    }

    function profileMenuItems()
    {
        return (
            <>
                <li className="menu-disabled"><span>Profile</span></li>
                <li>
                    <button type="button" onClick={() => logout({ logoutParams: { returnTo: `${window.location.origin}/` } })}>
                        Sign out
                    </button>
                </li>
            </>
        )
    }

    return (
        <main data-theme={theme} className="min-h-screen min-w-0 w-full bg-base-200 px-4 pt-5 pb-[calc(5rem+env(safe-area-inset-bottom))] text-base-content sm:px-8 sm:py-8">
            <header className="navbar mx-auto max-w-6xl border-b border-base-300 px-0 pb-4">
                {/* Left: the app title, which takes the user back to the collection (the home page for now). */}
                <div className="navbar-start min-w-0 w-auto flex-1 justify-center sm:justify-start">
                    <Link className="btn btn-ghost h-auto min-h-10 max-w-full px-2 py-2 text-base font-bold whitespace-normal text-primary sm:text-lg" to="/collection">
                        Track My Board Games
                    </Link>
                </div>

                {/* Right: action button, dark mode toggle, and the profile menu. */}
                <div className="navbar-end hidden w-auto shrink-0 gap-1 sm:flex sm:gap-2">
                    {/* Create menu: a plus icon that opens Add A Play, Add A Game, and the nested Shelf Background options. */}
                    <div className="dropdown dropdown-end">
                        <div className="btn btn-primary btn-sm btn-circle" tabIndex={0} role="button" aria-label="Open create menu">
                            <svg className="size-5 fill-none stroke-current" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" strokeWidth="2.5" strokeLinecap="round" aria-hidden="true">
                                <path d="M12 5v14M5 12h14" />
                            </svg>
                        </div>
                        <ul className="menu dropdown-content z-10 mt-3 w-56 rounded-box bg-base-100 p-2 shadow" tabIndex={0}>
                            {createMenuItems()}
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
                            {profileMenuItems()}
                        </ul>
                    </div>
                </div>
            </header>
            {children}
            {/* DaisyUI includes the phone's bottom safe area in the dock height. The main padding leaves room for it. */}
            <nav className="dock dock-md z-20 border-base-300 bg-base-100 text-base-content sm:hidden print:hidden" aria-label="Mobile navigation">
                <NavLink to="/collection" end>
                    <svg className="size-6 fill-none stroke-current" viewBox="0 0 24 24" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="m3 10 9-7 9 7M5 9v12h14V9M9 21v-7h6v7" />
                    </svg>
                    <span className="dock-label">Collection</span>
                </NavLink>
                <div className="dropdown dropdown-top">
                    <button className="flex size-full flex-col items-center justify-center gap-1" type="button" aria-label="Open mobile create menu">
                        <svg className="size-6 fill-none stroke-current" viewBox="0 0 24 24" strokeWidth="2.5" strokeLinecap="round" aria-hidden="true">
                            <path d="M12 5v14M5 12h14" />
                        </svg>
                        <span className="dock-label">Add</span>
                    </button>
                    <ul className="menu dropdown-content bottom-full left-1/2 mb-3 max-h-[60svh] w-56 max-w-[calc(100vw-2rem)] -translate-x-1/2 overflow-y-auto rounded-box border border-base-300 bg-base-100 p-2 shadow-lg" tabIndex={0}>
                        {createMenuItems()}
                    </ul>
                </div>
                <button type="button" aria-label="Toggle dark mode" aria-pressed={theme === 'boardgame-dark'} onClick={() => setTheme(theme === 'boardgame-dark' ? 'boardgame-light' : 'boardgame-dark')}>
                    <svg className="size-6 fill-none stroke-current" viewBox="0 0 24 24" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M21 12.8A9 9 0 0 1 11.2 3 9 9 0 1 0 21 12.8Z" />
                    </svg>
                    <span className="dock-label">Theme</span>
                </button>
                <div className="dropdown dropdown-top dropdown-end">
                    <button className="flex size-full flex-col items-center justify-center gap-1" type="button" aria-label="Open mobile profile menu">
                        <span className="avatar avatar-placeholder">
                            <span className="w-7 rounded-full bg-primary text-primary-content"><span>{userInitial}</span></span>
                        </span>
                        <span className="dock-label">Profile</span>
                    </button>
                    <ul className="menu dropdown-content bottom-full right-0 mb-3 w-52 max-w-[calc(100vw-2rem)] rounded-box border border-base-300 bg-base-100 p-2 shadow-lg" tabIndex={0}>
                        {profileMenuItems()}
                    </ul>
                </div>
            </nav>
        </main>
    )
}

export default AppLayout
