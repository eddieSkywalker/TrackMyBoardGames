import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'
import BoardGameForm from '../components/BoardGameForm'
import BoardGameCollection from '../components/BoardGameCollection'
import type { BoardGame, GameFormValues } from '../types/boardGame'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const apiAudience = import.meta.env.VITE_AUTH0_AUDIENCE || 'https://trackmyboardgames-api'
const themeStorageKey = 'track-my-board-games-theme'

type CollectionTheme = 'boardgame-light' | 'boardgame-dark'

function getSavedTheme(): CollectionTheme
{
    try
    {
        return window.localStorage.getItem(themeStorageKey) === 'boardgame-dark'
            ? 'boardgame-dark'
            : 'boardgame-light'
    }
    catch
    {
        return 'boardgame-light'
    }
}

async function responseError(response: Response)
{
    // Prefer a useful message from Spring; fall back to the HTTP status if the body is not JSON.
    const body = await response.json().catch(() => null) as { detail?: string; message?: string } | null
    return body?.detail ?? body?.message ?? `The backend returned HTTP ${response.status}.`
}

// Shows the signed-in user's games and lets them add games to their own collection.
function CollectionPage()
{
    const { user, logout, getAccessTokenSilently } = useAuth0()
    const [games, setGames] = useState<BoardGame[]>([])
    const [theme, setTheme] = useState<CollectionTheme>(getSavedTheme)
    const [isLoading, setIsLoading] = useState(true)
    const [isSaving, setIsSaving] = useState(false)
    const [isAddFormOpen, setIsAddFormOpen] = useState(false)
    const [editingGame, setEditingGame] = useState<BoardGame | null>(null)
    const [deletingGameId, setDeletingGameId] = useState<string | null>(null)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [successMessage, setSuccessMessage] = useState<string | null>(null)

    useEffect(() =>
    {
        try
        {
            window.localStorage.setItem(themeStorageKey, theme)
        }
        catch
        {
            // The collection remains usable when browser storage is unavailable.
        }
    }, [theme])

    // Apply the theme to <html> so the area around #root (its padding) follows it too.
    useEffect(() =>
    {
        document.documentElement.setAttribute('data-theme', theme)
        return () => document.documentElement.removeAttribute('data-theme')
    }, [theme])

    // Load this user's games with the read:games permission when the page opens.
    useEffect(() =>
    {
        // Prevent a finished request from updating React state after this page has unmounted.
        let isCurrent = true

        async function loadGames()
        {
            setIsLoading(true)
            setErrorMessage(null)

            try
            {
                // Request a token limited to reading games, then send it as a Bearer token to Spring.
                const accessToken = await getAccessTokenSilently({
                    authorizationParams: { audience: apiAudience, scope: 'read:games' },
                })
                const response = await fetch(`${apiBaseUrl}/api/games`, {
                    headers: { Authorization: `Bearer ${accessToken}` },
                })

                if (!response.ok)
                {
                    throw new Error(await responseError(response))
                }

                const results = await response.json() as BoardGame[]
                if (isCurrent) setGames(results)
            }
            catch (error)
            {
                if (isCurrent)
                {
                    setErrorMessage(error instanceof Error ? error.message : 'Could not load your games.')
                }
            }
            finally
            {
                if (isCurrent)
                {
                    setIsLoading(false)
                }
            }
        }

        void loadGames()
        return () =>
        {
            // Mark this page as inactive so the load request can finish safely in the background.
            isCurrent = false
        }
    }, [getAccessTokenSilently])

    // Create or update a game; the backend assigns and verifies ownership from the validated token.
    async function handleSubmit(values: GameFormValues)
    {
        setIsSaving(true)
        setErrorMessage(null)
        setSuccessMessage(null)

        try
        {
            const accessToken = await getAccessTokenSilently({
                authorizationParams: { audience: apiAudience, scope: 'write:games' },
            })
            // Send only game details; Spring determines the owner from the validated token.
            const response = await fetch(
                editingGame ? `${apiBaseUrl}/api/games/${editingGame.id}` : `${apiBaseUrl}/api/games`,
                {
                    method: editingGame ? 'PUT' : 'POST',
                    headers: {
                        Authorization: `Bearer ${accessToken}`,
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        name: values.name.trim(),
                        minPlayers: values.minPlayers ? Number(values.minPlayers) : undefined,
                        maxPlayers: values.maxPlayers ? Number(values.maxPlayers) : undefined,
                        bestPlayers: values.bestPlayers.trim() || undefined,
                        playTimeMinutes: values.playTimeMinutes ? Number(values.playTimeMinutes) : undefined,
                        notes: values.notes.trim() || undefined,
                    }),
                },
            )

            if (!response.ok)
            {
                throw new Error(await responseError(response))
            }

            const savedGame = await response.json() as BoardGame
            setGames(currentGames =>
            {
                const updatedGames = currentGames.filter(game => game.id !== savedGame.id)
                updatedGames.push(savedGame)
                updatedGames.sort((left, right) => left.name.localeCompare(right.name))
                return updatedGames
            })
            setIsAddFormOpen(false)
            setEditingGame(null)
            setSuccessMessage(editingGame
                ? `${savedGame.name} was updated.`
                : `${savedGame.name} was added to your collection.`)
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not save this game.')
        }
        finally
        {
            setIsSaving(false)
        }
    }

    // Delete only after the user confirms; the backend still enforces collection ownership.
    async function handleDelete(game: BoardGame)
    {
        if (!window.confirm(`Delete ${game.name} from your collection?`)) return

        setDeletingGameId(game.id)
        setErrorMessage(null)
        setSuccessMessage(null)

        try
        {
            const accessToken = await getAccessTokenSilently({
                authorizationParams: { audience: apiAudience, scope: 'write:games' },
            })
            const response = await fetch(`${apiBaseUrl}/api/games/${game.id}`, {
                method: 'DELETE',
                headers: { Authorization: `Bearer ${accessToken}` },
            })

            if (!response.ok)
            {
                throw new Error(await responseError(response))
            }

            setGames(currentGames => currentGames.filter(existingGame => existingGame.id !== game.id))
            if (editingGame?.id === game.id) setEditingGame(null)
            setSuccessMessage(`${game.name} was deleted from your collection.`)
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not delete this game.')
        }
        finally
        {
            setDeletingGameId(null)
        }
    }

    function openAddForm()
    {
        setEditingGame(null)
        setIsAddFormOpen(true)
        setErrorMessage(null)
        setSuccessMessage(null)
    }

    function openEditForm(game: BoardGame)
    {
        setIsAddFormOpen(false)
        setEditingGame(game)
        setErrorMessage(null)
        setSuccessMessage(null)
    }

    function closeForm()
    {
        setIsAddFormOpen(false)
        setEditingGame(null)
        setErrorMessage(null)
    }

    const isFormOpen = isAddFormOpen || editingGame !== null

    // Render the form and the collection's loading, empty, error, and game-list states.
    return (
        <main data-theme={theme} className="min-h-screen w-full bg-base-200 px-4 py-5 text-base-content sm:px-8 sm:py-8">
            <header className="mx-auto flex max-w-6xl items-center justify-between border-b border-base-300 pb-4">
                <Link className="flex items-center gap-3 text-sm font-bold text-primary no-underline" to="/collection">
                    <span className="grid size-9 place-items-center rounded border border-base-300 font-serif text-sm">TB</span>
                    <span>Track My Board Games</span>
                </Link>
                <div className="flex items-center gap-3">
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
            <section className="mx-auto max-w-6xl py-10 sm:py-14">
                <div className="flex flex-col justify-between gap-6 border-b border-base-300 pb-7 sm:flex-row sm:items-end">
                    <div>
                        <p className="mb-3 text-xs font-bold tracking-[0.16em] text-secondary">YOUR COLLECTION</p>
                        <h1 className="font-serif text-4xl leading-tight text-base-content sm:text-5xl">Game shelf</h1>
                        <p className="mt-3 text-sm text-base-content/70">Welcome, {user?.name ?? user?.email ?? 'player'}.</p>
                    </div>
                    <button
                        className="btn btn-primary w-fit px-5"
                        type="button"
                        onClick={isFormOpen ? closeForm : openAddForm}
                    >
                        {isFormOpen ? 'Cancel' : 'Add a game'}
                    </button>
                </div>

                {errorMessage && (
                    <p className="alert alert-error mt-5 text-sm" role="alert">
                        {errorMessage}
                    </p>
                )}
                {successMessage && (
                    <p className="alert alert-success mt-5 text-sm" role="status">
                        {successMessage}
                    </p>
                )}

                {isFormOpen && (
                    <BoardGameForm
                        key={editingGame?.id ?? 'new-game'}
                        initialGame={editingGame ?? undefined}
                        isSaving={isSaving}
                        onCancel={closeForm}
                        onSubmit={handleSubmit}
                    />
                )}

                <div className="mt-8">
                    <div className="mb-3 flex items-baseline justify-between">
                        <h2 className="text-base font-bold text-base-content">Your games</h2>
                        {!isLoading && <span className="text-xs text-base-content/70">{games.length} {games.length === 1 ? 'game' : 'games'}</span>}
                    </div>

                    {isLoading ? (
                        <p className="rounded-box border border-base-300 bg-base-100 px-4 py-6 text-sm text-base-content/70" role="status">
                            Loading your games...
                        </p>
                    ) : games.length === 0 ? (
                        <div className="rounded-box border border-dashed border-base-300 bg-base-100 px-5 py-10 text-center">
                            <p className="font-serif text-2xl text-base-content">Your shelf is ready.</p>
                            <p className="mt-2 text-sm text-base-content/70">Add your first game to start building your collection.</p>
                            {!isFormOpen && (
                                <button
                                    className="btn btn-primary mt-5 px-5"
                                    type="button"
                                    onClick={openAddForm}
                                >
                                    Add your first game
                                </button>
                            )}
                        </div>
                    ) : (
                        <BoardGameCollection
                            games={games}
                            onEdit={openEditForm}
                            onDelete={handleDelete}
                            deletingGameId={deletingGameId}
                        />
                    )}
                </div>
            </section>
        </main>
    )
}

export default CollectionPage