import { useEffect, useState, type FormEvent } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const apiAudience = import.meta.env.VITE_AUTH0_AUDIENCE || 'https://trackmyboardgames-api'

type BoardGame = {
    id: string
    name: string
    minPlayers: number | null
    maxPlayers: number | null
    playTimeMinutes: number | null
    notes: string | null
    createdAt: string
    updatedAt: string
}

type GameForm = {
    name: string
    minPlayers: string
    maxPlayers: string
    playTimeMinutes: string
    notes: string
}

const emptyGameForm: GameForm = {
    name: '',
    minPlayers: '',
    maxPlayers: '',
    playTimeMinutes: '',
    notes: '',
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
    const [form, setForm] = useState<GameForm>(emptyGameForm)
    const [isLoading, setIsLoading] = useState(true)
    const [isSaving, setIsSaving] = useState(false)
    const [isFormOpen, setIsFormOpen] = useState(false)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [successMessage, setSuccessMessage] = useState<string | null>(null)

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

    // Send the form with write:games; the backend assigns the owner from the validated token.
    async function handleSubmit(event: FormEvent<HTMLFormElement>)
    {
        event.preventDefault()
        setIsSaving(true)
        setErrorMessage(null)
        setSuccessMessage(null)

        try
        {
            const accessToken = await getAccessTokenSilently({
                authorizationParams: { audience: apiAudience, scope: 'write:games' },
            })
            // Send only game details; Spring determines the owner from the validated token.
            const response = await fetch(`${apiBaseUrl}/api/games`, {
                method: 'POST',
                headers: {
                    Authorization: `Bearer ${accessToken}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    name: form.name.trim(),
                    minPlayers: form.minPlayers ? Number(form.minPlayers) : undefined,
                    maxPlayers: form.maxPlayers ? Number(form.maxPlayers) : undefined,
                    playTimeMinutes: form.playTimeMinutes ? Number(form.playTimeMinutes) : undefined,
                    notes: form.notes.trim() || undefined,
                }),
            })

            if (!response.ok)
            {
                throw new Error(await responseError(response))
            }

            const createdGame = await response.json() as BoardGame
            // Use the created record returned by Spring to update the list without fetching it again.
            setGames(currentGames => [...currentGames, createdGame].sort((left, right) =>
                left.name.localeCompare(right.name)))
            setForm(emptyGameForm)
            setIsFormOpen(false)
            setSuccessMessage(`${createdGame.name} was added to your collection.`)
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not add this game.')
        }
        finally
        {
            setIsSaving(false)
        }
    }

    // Render the add form and the collection's loading, empty, error, and game-list states.
    return (
        <main className="min-h-screen w-full bg-[#e9eeea] px-4 py-5 text-[#273a32] sm:px-8 sm:py-8">
            <header className="mx-auto flex max-w-6xl items-center justify-between border-b border-[#cdd5cf] pb-4">
                <Link className="flex items-center gap-3 text-sm font-bold text-[#203c33] no-underline" to="/collection">
                    <span className="grid size-9 place-items-center rounded border border-[#7d978b] font-serif text-sm">TB</span>
                    <span>Track My Board Games</span>
                </Link>
                <button
                    className="rounded px-3 py-2 text-sm font-semibold text-[#8b4c32] hover:bg-[#dfe7e0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#d17b4c]"
                    type="button"
                    onClick={() => logout({ logoutParams: { returnTo: `${window.location.origin}/` } })}
                >
                    Sign out
                </button>
            </header>
            <section className="mx-auto max-w-6xl py-10 sm:py-14">
                <div className="flex flex-col justify-between gap-6 border-b border-[#cdd5cf] pb-7 sm:flex-row sm:items-end">
                    <div>
                        <p className="mb-3 text-xs font-bold tracking-[0.16em] text-[#a45436]">YOUR COLLECTION</p>
                        <h1 className="font-serif text-4xl leading-tight text-[#20352e] sm:text-5xl">Game shelf</h1>
                        <p className="mt-3 text-sm text-[#65736b]">Welcome, {user?.name ?? user?.email ?? 'player'}.</p>
                    </div>
                    <button
                        className="w-fit rounded bg-[#203c33] px-5 py-3 text-sm font-bold text-[#fffdf7] hover:bg-[#2e5548] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#d17b4c]"
                        type="button"
                        onClick={() =>
                        {
                            setIsFormOpen(open => !open)
                            setErrorMessage(null)
                            setSuccessMessage(null)
                        }}
                    >
                        {isFormOpen ? 'Cancel' : 'Add a game'}
                    </button>
                </div>

                {errorMessage && (
                    <p className="mt-5 rounded border border-[#e2b6a9] bg-[#f7e9e5] px-4 py-3 text-sm text-[#792c23]" role="alert">
                        {errorMessage}
                    </p>
                )}
                {successMessage && (
                    <p className="mt-5 rounded border border-[#a9cbb4] bg-[#edf5ef] px-4 py-3 text-sm text-[#286344]" role="status">
                        {successMessage}
                    </p>
                )}

                {isFormOpen && (
                    <form className="mt-7 rounded-md border border-[#cdd5cf] bg-[#fbfaf6] p-5 sm:p-7" onSubmit={handleSubmit}>
                        <h2 className="font-serif text-2xl text-[#20352e]">Add a board game</h2>
                        <p className="mt-1 text-sm text-[#65736b]">Name is required. The other details are optional.</p>
                        <div className="mt-6 grid gap-4 sm:grid-cols-2">
                            <label className="grid gap-1.5 text-sm font-semibold sm:col-span-2">
                                Game name
                                <input
                                    className="min-h-11 rounded border border-[#bbc8be] bg-white px-3 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                                    value={form.name}
                                    onChange={event => setForm({ ...form, name: event.target.value })}
                                    maxLength={200}
                                    required
                                    autoFocus
                                />
                            </label>
                            <label className="grid gap-1.5 text-sm font-semibold">
                                Minimum players
                                <input
                                    className="min-h-11 rounded border border-[#bbc8be] bg-white px-3 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                                    type="number"
                                    min="1"
                                    step="1"
                                    value={form.minPlayers}
                                    onChange={event => setForm({ ...form, minPlayers: event.target.value })}
                                />
                            </label>
                            <label className="grid gap-1.5 text-sm font-semibold">
                                Maximum players
                                <input
                                    className="min-h-11 rounded border border-[#bbc8be] bg-white px-3 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                                    type="number"
                                    min="1"
                                    step="1"
                                    value={form.maxPlayers}
                                    onChange={event => setForm({ ...form, maxPlayers: event.target.value })}
                                />
                            </label>
                            <label className="grid gap-1.5 text-sm font-semibold">
                                Play time in minutes
                                <input
                                    className="min-h-11 rounded border border-[#bbc8be] bg-white px-3 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                                    type="number"
                                    min="1"
                                    step="1"
                                    value={form.playTimeMinutes}
                                    onChange={event => setForm({ ...form, playTimeMinutes: event.target.value })}
                                />
                            </label>
                            <label className="grid gap-1.5 text-sm font-semibold sm:col-span-2">
                                Notes
                                <textarea
                                    className="min-h-24 rounded border border-[#bbc8be] bg-white px-3 py-2 font-normal outline-none focus:border-[#27634e] focus:ring-2 focus:ring-[#27634e]/20"
                                    value={form.notes}
                                    onChange={event => setForm({ ...form, notes: event.target.value })}
                                    maxLength={4000}
                                />
                            </label>
                        </div>
                        <div className="mt-5 flex flex-wrap items-center gap-3">
                            <button
                                className="min-h-11 rounded bg-[#203c33] px-5 text-sm font-bold text-[#fffdf7] hover:bg-[#2e5548] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#d17b4c] disabled:cursor-wait disabled:opacity-60"
                                type="submit"
                                disabled={isSaving}
                            >
                                {isSaving ? 'Saving...' : 'Save game'}
                            </button>
                            <span className="text-xs text-[#65736b]">Saved to your account’s collection.</span>
                        </div>
                    </form>
                )}

                <div className="mt-8">
                    <div className="mb-3 flex items-baseline justify-between">
                        <h2 className="text-base font-bold text-[#20352e]">Your games</h2>
                        {!isLoading && <span className="text-xs text-[#65736b]">{games.length} {games.length === 1 ? 'game' : 'games'}</span>}
                    </div>

                    {isLoading ? (
                        <p className="rounded border border-[#cdd5cf] bg-[#fbfaf6] px-4 py-6 text-sm text-[#65736b]" role="status">
                            Loading your games...
                        </p>
                    ) : games.length === 0 ? (
                        <div className="rounded border border-dashed border-[#aebdb2] bg-[#f3f5f1] px-5 py-10 text-center">
                            <p className="font-serif text-2xl text-[#20352e]">Your shelf is ready.</p>
                            <p className="mt-2 text-sm text-[#65736b]">Add your first game to start building your collection.</p>
                            {!isFormOpen && (
                                <button
                                    className="mt-5 rounded bg-[#203c33] px-5 py-2.5 text-sm font-bold text-[#fffdf7] hover:bg-[#2e5548]"
                                    type="button"
                                    onClick={() => setIsFormOpen(true)}
                                >
                                    Add your first game
                                </button>
                            )}
                        </div>
                    ) : (
                        <ul className="divide-y divide-[#dce1da] border-y border-[#dce1da] bg-[#fbfaf6]">
                            {games.map(game => (
                                <li className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5" key={game.id}>
                                    <div>
                                        <h3 className="font-semibold text-[#20352e]">{game.name}</h3>
                                        {game.notes && <p className="mt-1 max-w-2xl text-sm text-[#65736b]">{game.notes}</p>}
                                    </div>
                                    <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-[#65736b]">
                                        {(game.minPlayers || game.maxPlayers) && (
                                            <span>{game.minPlayers ?? '?'}–{game.maxPlayers ?? '?'} players</span>
                                        )}
                                        {game.playTimeMinutes && <span>{game.playTimeMinutes} min</span>}
                                    </div>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </section>
        </main>
    )
}

export default CollectionPage