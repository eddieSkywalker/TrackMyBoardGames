import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { useLocation, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import BoardGameForm from '../components/BoardGameForm'
import BoardGameCollection from '../components/BoardGameCollection'
import DeleteConfirmationDialog from '../components/DeleteConfirmationDialog'
import ShelfBackground from '../components/ShelfBackground'
import CollectionStatistics from '../components/CollectionStatistics'
import { apiAudience, apiBaseUrl, responseError } from '../api/apiHelpers'
import type { BoardGame, GameFormValues } from '../types/boardGame'

// Shows the signed-in user's games and lets them add games to their own collection.
function CollectionPage()
{
    const { getAccessTokenSilently, user } = useAuth0()
    const [games, setGames] = useState<BoardGame[]>([])
    const [isLoading, setIsLoading] = useState(true)
    const [isSaving, setIsSaving] = useState(false)
    const [isAddFormOpen, setIsAddFormOpen] = useState(false)
    const [editingGame, setEditingGame] = useState<BoardGame | null>(null)
    const [deletingGameId, setDeletingGameId] = useState<string | null>(null)
    const [gameToDelete, setGameToDelete] = useState<BoardGame | null>(null)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [successMessage, setSuccessMessage] = useState<string | null>(null)
    const location = useLocation()
    const navigate = useNavigate()

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
    async function handleSubmit(values: GameFormValues, coverImage: string | null)
    {
        setIsSaving(true)
        setErrorMessage(null)
        setSuccessMessage(null)

        try
        {
            const accessToken = await getAccessTokenSilently({
                authorizationParams: { audience: apiAudience, scope: 'write:games' },
            })
            // Send the game details and cover photo (null removes it); Spring determines the owner from the validated token.
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
                        edition: values.edition.trim() || undefined,
                        minPlayers: values.minPlayers ? Number(values.minPlayers) : undefined,
                        maxPlayers: values.maxPlayers ? Number(values.maxPlayers) : undefined,
                        bestPlayers: values.bestPlayers.trim() || undefined,
                        playTimeMinutes: values.playTimeMinutes ? Number(values.playTimeMinutes) : undefined,
                        notes: values.notes.trim() || undefined,
                        coverImage,
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

    // The navbar's "Add A Game!" option navigates here with an action in the router state.
    // Setting state while rendering (guarded by the handled key) is React's way to react to a changed value without an effect.
    const navAction = (location.state as { action?: string } | null)?.action
    const [handledKey, setHandledKey] = useState('')
    if (navAction === 'add-game' && handledKey !== location.key)
    {
        setHandledKey(location.key)
        openAddForm()
    }

    // Clear the action so refreshing the page doesn't reopen the form.
    useEffect(() =>
    {
        if (navAction) navigate(location.pathname, { replace: true, state: null })
    }, [navAction, navigate, location.pathname])
    // Render the form and the collection's loading, empty, error, and game-list states.
    return (
        <AppLayout>
            <section className="mx-auto max-w-6xl py-10 sm:py-7">
                <ShelfBackground>
                <div className="flex flex-col justify-between gap-6 border-b border-base-300 pb-7 sm:flex-row sm:items-end">
                    <div>
                        <p className="mb-3 text-xs font-bold tracking-[0.16em] text-secondary">YOUR COLLECTION</p>
                        <h1 className="font-serif text-4xl leading-tight text-base-content sm:text-5xl">Game shelf</h1>
                    </div>
                </div>
                </ShelfBackground>

                {/* Changing accounts changes the key, so React clears the previous account's statistics.
                    If Auth0 has not supplied a user ID (sub), ?? uses 'signed-in-user' as a temporary key.
                    This key only helps React track the component; it is not sent to the backend. */}
                <CollectionStatistics key={user?.sub ?? 'signed-in-user'} />

                {errorMessage && (
                    <p className="alert alert-soft alert-error mt-5 text-sm in-data-[theme=boardgame-light]:border-[#e2b6a9] in-data-[theme=boardgame-light]:bg-[#f7e9e5] in-data-[theme=boardgame-light]:text-[#792c23]" role="alert">
                        {errorMessage}
                    </p>
                )}
                {successMessage && (
                    <p className="alert alert-soft alert-success mt-5 text-sm in-data-[theme=boardgame-light]:border-[#a9cbb4] in-data-[theme=boardgame-light]:bg-[#edf5ef] in-data-[theme=boardgame-light]:text-[#286344]" role="status">
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

                <div className="mt-8" hidden={isFormOpen}>
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
                            onDelete={setGameToDelete}
                            deletingGameId={deletingGameId}
                        />
                    )}
                </div>
            </section>
            {gameToDelete && (
                <DeleteConfirmationDialog
                    key={gameToDelete.id}
                    title="Delete game?"
                    description={`This will permanently delete ${gameToDelete.name} and its associated play history and QR label. This cannot be undone.`}
                    confirmationValue={gameToDelete.name}
                    confirmLabel="Delete game"
                    onConfirm={() => handleDelete(gameToDelete)}
                    onCancel={() => setGameToDelete(null)}
                />
            )}
        </AppLayout>
    )
}

export default CollectionPage