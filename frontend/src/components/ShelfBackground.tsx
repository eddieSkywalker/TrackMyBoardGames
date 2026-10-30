import { useEffect, useRef, useState, type ReactNode, type TouchEvent } from 'react'
import { useLocation } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { apiAudience, apiBaseUrl, responseError } from '../api/apiHelpers'
import { blobToDataUrl } from '../api/imageApi'
import ImagePicker from './ImagePicker'

type ShelfBackgroundProps = {
    children: ReactNode
    collectionId: string
    navigation: ReactNode
    onSwipe: (direction: 'next' | 'previous') => void
    navigationDisabled: boolean
}

/**
 * Loads and edits the viewed collection's picture. Deliberate mobile swipes browse without changing the saved default.
 */
function ShelfBackground({ children, collectionId, navigation, onSwipe, navigationDisabled }: ShelfBackgroundProps)
{
    const { getAccessTokenSilently } = useAuth0()

    const [backgroundImage, setBackgroundImage] = useState<string | null>(null)
    const [isBusy, setIsBusy] = useState(false)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [isPickerOpen, setIsPickerOpen] = useState(false)
    const location = useLocation()
    const swipeStartPosition = useRef<{ horizontal: number; vertical: number } | null>(null)
    const collectionUrl = `${apiBaseUrl}/api/collection?collectionId=${encodeURIComponent(collectionId)}`

    // useEffect runs after the component first appears, and again if the token function changes.
    // It loads the saved background once, rather than making a request during every render.
    useEffect(() =>
    {
        let isCurrent = true

        async function loadShelf()
        {
            try
            {
                // Ask Auth0 for permission to read the collection.
                const accessToken = await getAccessTokenSilently({
                    authorizationParams: { audience: apiAudience, scope: 'read:games' },
                })

                // Fetch the collection, including its optional background image.
                const response = await fetch(collectionUrl, {
                    headers: { Authorization: `Bearer ${accessToken}` },
                })

                // Turn an unsuccessful HTTP response into a readable error.
                if (!response.ok)
                {
                    throw new Error(await responseError(response))
                }

                // Put the saved image into component state so React displays it.
                const collection = await response.json() as { backgroundImage: string | null }
                if (isCurrent)
                {
                    setBackgroundImage(collection.backgroundImage)
                }
            }
            catch (error)
            {
                // Show the error only if this component is still on screen.
                if (isCurrent)
                {
                    setErrorMessage(error instanceof Error ? error.message : 'Could not load the shelf background.')
                }
            }
        }

        void loadShelf()
        return () =>
        {
            // Ignore a late response if the user leaves this page before loading finishes.
            isCurrent = false
        }
    }, [getAccessTokenSilently, collectionUrl])

    // Get a token with permission to change the collection before saving or removing its picture.
    async function getWriteToken()
    {
        return await getAccessTokenSilently({
            authorizationParams: { audience: apiAudience, scope: 'write:games' },
        })
    }

    // Save the newly picked picture with the collection, replacing the old one.
    async function handlePicked(image: Blob)
    {
        // Disable the image controls and clear any message from the previous attempt.
        setIsBusy(true)
        setErrorMessage(null)

        try
        {
            // Convert the resized picture to text so it can be sent as JSON.
            const backgroundImage = await blobToDataUrl(image)

            // Send the updated background with a token allowed to write games and collections.
            const response = await fetch(collectionUrl, {
                method: 'PUT',
                headers: {
                    Authorization: `Bearer ${await getWriteToken()}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ backgroundImage }),
            })

            // Surface server-side validation or authorization errors.
            if (!response.ok)
            {
                throw new Error(await responseError(response))
            }

            // Use the saved value returned by the server to update the display.
            const collection = await response.json() as { backgroundImage: string | null }
            setBackgroundImage(collection.backgroundImage)
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not save the background.')
        }
        finally
        {
            // Re-enable the controls whether saving succeeded or failed.
            setIsBusy(false)
        }
    }

    // Save null so the shelf goes back to the plain look.
    async function handleRemove()
    {
        // Disable the image controls and clear any message from the previous attempt.
        setIsBusy(true)
        setErrorMessage(null)

        try
        {
            // Send null to tell the backend to clear the saved background.
            const response = await fetch(collectionUrl, {
                method: 'PUT',
                headers: {
                    Authorization: `Bearer ${await getWriteToken()}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ backgroundImage: null }),
            })

            // Surface server-side validation or authorization errors.
            if (!response.ok)
            {
                throw new Error(await responseError(response))
            }

            // Update the display using the value returned by the server.
            const collection = await response.json() as { backgroundImage: string | null }
            setBackgroundImage(collection.backgroundImage)
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not remove the background.')
        }
        finally
        {
            // Re-enable the controls whether removing succeeded or failed.
            setIsBusy(false)
        }
    }

    // The navbar menu navigates here with an action in the router state; location.key changes on every navigation.
    // Setting state while rendering (guarded by the handled key) is React's way to react to a changed value without an effect.
    // CollectionPage clears the router state afterwards, so refreshing doesn't repeat the action.
    const navAction = (location.state as { action?: string } | null)?.action
    const [handledKey, setHandledKey] = useState('')
    if (navAction === 'shelf-add' && handledKey !== location.key)
    {
        setHandledKey(location.key)
        setIsPickerOpen(true)
    }

    useEffect(() =>
    {
        // Starting on a later tick keeps React from treating the busy-state update as a synchronous effect update.
        if (navAction === 'shelf-remove')
        {
            void Promise.resolve().then(handleRemove)
        }
        // Only a new navigation should trigger this; handleRemove is re-created every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [navAction, location.key])

    /**
     * onTouchStart remembers where a single finger landed, unless controls or an ongoing action need the touch.
     */
    function handleTouchStart(event: TouchEvent<HTMLDivElement>)
    {
        swipeStartPosition.current = null

        const isDesktop = window.matchMedia('(min-width: 640px)').matches
        const cannotStartSwipe = isBusy || isPickerOpen || navigationDisabled || event.touches.length !== 1 || isDesktop

        if (cannotStartSwipe)
        {
            return
        }

        const touchedControl = event.target instanceof Element && event.target.closest('button, select, input, a')

        if (touchedControl)
        {
            return
        }

        const startingTouch = event.touches[0]
        swipeStartPosition.current = {
            horizontal: startingTouch.clientX,
            vertical: startingTouch.clientY,
        }
    }

    /**
     * onTouchCancel forgets an interrupted gesture so it cannot change collections later.
     */
    function handleTouchCancel()
    {
        swipeStartPosition.current = null
    }

    /**
     * onTouchEnd changes collection only for a deliberate horizontal swipe, leaving vertical scrolling alone.
     */
    function handleTouchEnd(event: TouchEvent<HTMLDivElement>)
    {
        const startingPosition = swipeStartPosition.current
        swipeStartPosition.current = null

        if (!startingPosition || isBusy || navigationDisabled || event.changedTouches.length !== 1)
        {
            return
        }

        const endingTouch = event.changedTouches[0]
        const horizontalMovement = endingTouch.clientX - startingPosition.horizontal
        const verticalMovement = endingTouch.clientY - startingPosition.vertical
        const horizontalDistance = Math.abs(horizontalMovement)
        const verticalDistance = Math.abs(verticalMovement)
        const isDeliberateHorizontalSwipe = horizontalDistance >= 60 && horizontalDistance > verticalDistance * 1.5

        if (isDeliberateHorizontalSwipe)
        {
            onSwipe(horizontalMovement > 0 ? 'next' : 'previous')
        }
    }

    return (
        <div>
            <div
                className="relative touch-pan-y rounded-box sm:touch-auto"
                onTouchStart={handleTouchStart}
                onTouchCancel={handleTouchCancel}
                onTouchEnd={handleTouchEnd}
            >
                {backgroundImage && (
                    <>
                        <img className="absolute inset-0 size-full rounded-box object-cover" src={backgroundImage} alt="" draggable={false} />
                        {/* A see-through layer in the theme colour keeps the heading text readable on any picture. */}
                        <div className="absolute inset-0 rounded-box bg-base-100/70" />
                    </>
                )}
                <div className={backgroundImage ? 'relative px-6 pt-6' : 'relative'}>{children}</div>
                <fieldset className="absolute right-2 top-2 z-10" disabled={isBusy || isPickerOpen}>
                    <legend className="sr-only">Collection navigation</legend>
                    {navigation}
                </fieldset>
            </div>

            {/* The navbar's Shelf Background > Add option opens this picker; Remove runs straight away. */}
            {isPickerOpen && (
                <div className="mt-3 flex flex-wrap items-center gap-3 text-sm">
                    <ImagePicker
                        maxSize={1600}
                        disabled={isBusy}
                        onPick={async image =>
                        {
                            await handlePicked(image)
                            setIsPickerOpen(false)
                        }}
                    />
                    <button className="btn btn-ghost btn-sm" type="button" onClick={() => setIsPickerOpen(false)}>
                        Cancel
                    </button>
                </div>
            )}
            {errorMessage && <p className="mt-2 text-xs text-error" role="alert">{errorMessage}</p>}
        </div>
    )
}

export default ShelfBackground
