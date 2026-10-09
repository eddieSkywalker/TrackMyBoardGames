import { useEffect, useState, type ReactNode } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { apiAudience, apiBaseUrl, responseError } from '../api/apiHelpers'
import { blobToDataUrl } from '../api/imageApi'
import ImagePicker from './ImagePicker'

type ShelfBackgroundProps = {
    children: ReactNode
}

// Draws the user's chosen picture behind whatever is placed inside (the "Game shelf" heading),
// and offers buttons to change or remove that picture. Changes are saved straight away.
function ShelfBackground({ children }: ShelfBackgroundProps)
{
    const { getAccessTokenSilently } = useAuth0()

    const [backgroundImage, setBackgroundImage] = useState<string | null>(null)
    const [isBusy, setIsBusy] = useState(false)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)

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
                const response = await fetch(`${apiBaseUrl}/api/collection`, {
                    headers: { Authorization: `Bearer ${accessToken}` },
                })

                // Turn an unsuccessful HTTP response into a readable error.
                if (!response.ok)
                {
                    throw new Error(await responseError(response))
                }

                // Put the saved image into component state so React displays it.
                const collection = await response.json() as { backgroundImage: string | null }
                if (isCurrent) setBackgroundImage(collection.backgroundImage)
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
    }, [getAccessTokenSilently])

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
            const response = await fetch(`${apiBaseUrl}/api/collection`, {
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
            const response = await fetch(`${apiBaseUrl}/api/collection`, {
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

    return (
        <div>
            <div className="relative overflow-hidden rounded-box">
                {backgroundImage && (
                    <>
                        <img className="absolute inset-0 size-full object-cover" src={backgroundImage} alt="" />
                        {/* A see-through layer in the theme colour keeps the heading text readable on any picture. */}
                        <div className="absolute inset-0 bg-base-100/70" />
                    </>
                )}
                <div className={backgroundImage ? 'relative px-6 pt-6' : 'relative'}>{children}</div>
            </div>

            <details className="mt-3 text-sm">
                <summary className="cursor-pointer text-base-content/70">Shelf background</summary>
                <div className="mt-3 flex flex-wrap items-center gap-3">
                    <ImagePicker maxSize={1600} disabled={isBusy} onPick={handlePicked} />
                    {backgroundImage && (
                        <button className="btn btn-ghost btn-sm" type="button" onClick={handleRemove} disabled={isBusy}>
                            Remove background
                        </button>
                    )}
                </div>
                {errorMessage && <p className="mt-2 text-xs text-error" role="alert">{errorMessage}</p>}
            </details>
        </div>
    )
}

export default ShelfBackground
