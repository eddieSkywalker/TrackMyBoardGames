import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { useLocation, useNavigate } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import BoardGameForm from '../components/BoardGameForm'
import BoardGameCollection from '../components/BoardGameCollection'
import CollectionStatistics from '../components/CollectionStatistics'
import ShelfBackground from '../components/ShelfBackground'
import CollectionNavigation from '../components/CollectionNavigation'
import { fetchCollections, saveDefaultCollection, type GameCollectionSummary } from '../api/collectionApi'
import { apiAudience, apiBaseUrl, responseError } from '../api/apiHelpers'
import type { BoardGame, GameFormValues } from '../types/boardGame'

/** Account changes start a fresh browsing session at that account's saved default. */
function CollectionPage()
{
    const { user } = useAuth0()
    const loginProvider = user?.iss ?? ''
    const userId = user?.sub ?? ''
    const accountKey = `${loginProvider}:${userId}`

    // A different account key makes React discard the previous account's browsing state.
    return (
        <CollectionBrowser key={accountKey} />
    )
}

/** Keeps temporary viewing separate from the saved default; starring does not reorder the current session. */
function CollectionBrowser()
{
    const { getAccessTokenSilently } = useAuth0()
    const [collections, setCollections] = useState<GameCollectionSummary[]>([])
    const [selectedId, setSelectedId] = useState<string | null>(null)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [savingDefault, setSavingDefault] = useState(false)
    const [retryCount, setRetryCount] = useState(0)

    // Load the collection list once per account or retry; the API helper puts the saved default first.
    useEffect(() =>
    {
        let isCurrentRequest = true
        const abortController = new AbortController()

        async function loadCollections()
        {
            try
            {
                const accessToken = await getAccessTokenSilently({
                    authorizationParams: { audience: apiAudience, scope: 'read:games' },
                })
                // The user may leave while Auth0 is preparing the token.
                if (!isCurrentRequest)
                {
                    return
                }

                const loadedCollections = await fetchCollections(accessToken, abortController.signal)

                if (isCurrentRequest)
                {
                    setCollections(loadedCollections)
                    setSelectedId(loadedCollections[0].id)
                }
            }
            catch (error)
            {
                if (isCurrentRequest)
                {
                    setErrorMessage(error instanceof Error ? error.message : 'Could not load your collections.')
                }
            }
        }
        void loadCollections()

        return () =>
        {
            // Cancel the old load and prevent its result from replacing a newer browsing session.
            isCurrentRequest = false
            abortController.abort()
        }
    }, [getAccessTokenSilently, retryCount])

    /** Saves the viewed collection as the default without moving it or changing the browsing order. */
    async function makeDefault()
    {
        if (!selectedId || savingDefault)
        {
            return
        }

        setSavingDefault(true)
        setErrorMessage(null)

        try
        {
            const accessToken = await getAccessTokenSilently({
                authorizationParams: { audience: apiAudience, scope: 'write:games' },
            })
            const savedDefault = await saveDefaultCollection(accessToken, selectedId)

            // Update stars only after the server confirms the save; leave the list order unchanged.
            setCollections(currentCollections => currentCollections.map(collection => ({
                ...collection,
                defaultCollection: collection.id === savedDefault.id,
            })))
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not save your default collection.')
        }
        finally
        {
            setSavingDefault(false)
        }
    }

    function retryLoadingCollections()
    {
        setErrorMessage(null)
        setRetryCount(currentCount => currentCount + 1)
    }

    function selectCollection(collectionId: string)
    {
        setSelectedId(collectionId)
        setErrorMessage(null)
    }

    const selectedCollection = collections.find(collection => collection.id === selectedId)

    // Do not display a shelf until its collection is known; offer a retry if loading failed.
    if (!selectedCollection)
    {
        return (
            <AppLayout>
                <section className="mx-auto max-w-6xl py-10" aria-busy={!errorMessage}>
                    {errorMessage ? (
                        <div className="alert alert-error" role="alert">
                            <span>{errorMessage}</span>
                            <button type="button" className="btn btn-sm" onClick={retryLoadingCollections}>
                                Retry
                            </button>
                        </div>
                    ) : <p role="status">Loading your collections...</p>}
                </section>
            </AppLayout>
        )
    }
    // A different collection key clears the previous shelf's games, forms, and pending loads.
    return (
        <CollectionView
            key={selectedCollection.id}
            collections={collections}
            selected={selectedCollection}
            savingDefault={savingDefault}
            defaultError={errorMessage}
            onMakeDefault={makeDefault}
            onSelect={selectCollection}
        />
    )
}

type CollectionViewProps = {
    collections: GameCollectionSummary[]
    selected: GameCollectionSummary
    savingDefault: boolean
    defaultError: string | null
    onMakeDefault: () => void
    onSelect: (id: string) => void
}

/** A new view resets old games, pending loads, and forms before displaying another collection. */
function CollectionView({ collections, selected, savingDefault, defaultError, onMakeDefault, onSelect }: CollectionViewProps)
{
    const { getAccessTokenSilently, user } = useAuth0()
    const [games, setGames] = useState<BoardGame[]>([])
    const [isLoading, setIsLoading] = useState(true)
    const [hasLoadedGames, setHasLoadedGames] = useState(false)
    const [isSaving, setIsSaving] = useState(false)
    const [isAddFormOpen, setIsAddFormOpen] = useState(false)
    const [editingGame, setEditingGame] = useState<BoardGame | null>(null)
    const [deletingGameId, setDeletingGameId] = useState<string | null>(null)
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
                const response = await fetch(`${apiBaseUrl}/api/games?collectionId=${encodeURIComponent(selected.id)}`, {
                    headers: { Authorization: `Bearer ${accessToken}` },
                })

                if (!response.ok)
                {
                    throw new Error(await responseError(response))
                }

                const results = await response.json() as BoardGame[]
                if (isCurrent)
                {
                    setGames(results)
                    setHasLoadedGames(true)
                }
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
    }, [getAccessTokenSilently, selected.id])

    // Create or update a game; the backend assigns and verifies ownership from the validated token.
    async function handleSubmit(values: GameFormValues, coverImage: string | null)
    {
        setIsSaving(true)
        setErrorMessage(null)
        setSuccessMessage(null)

        try
        {
            // Determine the access token for the request based on the required scope.
            const accessToken = await getAccessTokenSilently({authorizationParams: { audience: apiAudience, scope: 'write:games' }})
            const requestUrl = editingGame
                ? `${apiBaseUrl}/api/games/${editingGame.id}`
                : `${apiBaseUrl}/api/games?collectionId=${encodeURIComponent(selected.id)}`

            // Send the game details and cover photo (null removes it); Spring determines the owner from the validated token.
            const response = await fetch(
                requestUrl,
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

            // Read the saved game returned by the backend and update the list without reloading the page.
            const savedGame = await response.json() as BoardGame
            setGames(currentGames =>
            {
                // Remove the old version if this was an edit, add the saved version, then sort by name.
                const updatedGames = currentGames.filter(game => game.id !== savedGame.id)
                updatedGames.push(savedGame)
                updatedGames.sort((left, right) => left.name.localeCompare(right.name))
                return updatedGames
            })

            // Close the form, clear the game being edited, and show the user that the save succeeded.
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
    const navigationDisabled = isFormOpen || isSaving || deletingGameId !== null || savingDefault
    const selectedIndex = collections.findIndex(collection => collection.id === selected.id)

    function selectCollection(id: string)
    {
        if (!navigationDisabled && id !== selected.id) onSelect(id)
    }

    function swipeCollection(direction: 'next' | 'previous')
    {
        const nextIndex = selectedIndex + (direction === 'next' ? 1 : -1)
        if (nextIndex >= 0 && nextIndex < collections.length) selectCollection(collections[nextIndex].id)
    }

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
                <ShelfBackground key={selected.id} collectionId={selected.id} onSwipe={swipeCollection}
                    navigationDisabled={navigationDisabled}
                    navigation={<CollectionNavigation collections={collections} selected={selected}
                        disabled={navigationDisabled} savingDefault={savingDefault}
                        onSelect={selectCollection} onMakeDefault={onMakeDefault} />}>
                <div className="flex flex-col justify-between gap-6 border-b border-base-300 pb-7 sm:flex-row sm:items-end">
                    <div className="min-w-0 pt-12">
                        <p className="mb-3 text-xs font-bold tracking-[0.16em] text-secondary">YOUR COLLECTION</p>
                        <h1 className="font-serif text-4xl leading-tight text-base-content sm:text-5xl">Game Shelf</h1>
                        <p className="mt-2 break-words text-lg font-semibold" role="status" aria-live="polite">{selected.name}</p>
                        {collections.length > 1 && (
                            <>
                                <p className="mt-2 text-xs text-base-content/70 sm:hidden">
                                    {selectedIndex + 1} of {collections.length}. Swipe right for next, left for previous.
                                </p>
                                {isFormOpen && <p className="mt-2 text-xs">Save or cancel the form before changing collections.</p>}
                            </>
                        )}
                    </div>
                </div>
                </ShelfBackground>

                {/* Start with fresh statistics when the signed-in account changes. */}
                {collections.length > 1 && (
                    <h2 className="mt-6 text-sm font-semibold">All collections statistics (not just this shelf)</h2>
                )}
                <CollectionStatistics key={user?.sub ?? 'signed-in-user'} games={games} />

                {defaultError && <p className="alert alert-error mt-5 text-sm" role="alert">{defaultError}</p>}
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

                <div className="mt-8">
                    <div className="mb-3 flex items-baseline justify-between">
                        <h2 className="text-base font-bold text-base-content">Your games</h2>
                    </div>

                    {isLoading ? (
                        <p className="rounded-box border border-base-300 bg-base-100 px-4 py-6 text-sm text-base-content/70" role="status">
                            Loading your games...
                        </p>
                    ) : !hasLoadedGames ? (
                        <p className="mt-4 text-sm" role="status">Could not load this collection. Reload the page to try again.</p>
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
        </AppLayout>
    )
}

export default CollectionPage