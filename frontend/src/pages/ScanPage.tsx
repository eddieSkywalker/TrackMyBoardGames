import { useEffect, useState, type ChangeEvent } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Scanner, type IDetectedBarcode, type IScannerError } from '@yudiel/react-qr-scanner'
import { BarcodeDetector } from 'barcode-detector/ponyfill'
import { Link, useNavigate, useParams } from 'react-router-dom'
import AppLayout from '../components/AppLayout'
import { apiAudience } from '../api/apiHelpers'
import { fetchScannedGame, tokenFromScannedText } from '../api/labelApi'

// Turn a camera problem into a message the user can act on.
function cameraMessage(error: IScannerError): string
{
    switch (error.kind)
    {
        case 'permission-denied':
            return 'Camera access was blocked. Allow the camera for this site in your browser settings, or choose a photo of the QR code instead.'
        case 'no-camera':
            return 'No camera was found on this device. You can choose a photo of the QR code instead.'
        case 'in-use':
            return 'The camera is being used by another app. Close it and reload, or choose a photo instead.'
        case 'insecure-context':
            return 'Browsers only allow the camera on secure (https) pages. You can choose a photo of the QR code instead.'
        default:
            return 'The camera could not be started. You can choose a photo of the QR code instead.'
    }
}

// Two ways in:
//  - /scan: shows the camera (or a photo picker) and reads a QR code.
//  - /scan/:token: the address stored inside every QR code. It looks up the game and opens it.
// The camera simply sends the user to /scan/:token, so there is only one place that does the lookup.
function ScanPage()
{
    // getAccessTokenSilently asks Auth0 for the token the backend needs; navigate changes the page from code.
    const { getAccessTokenSilently } = useAuth0()
    const navigate = useNavigate()
    // The :token part of /scan/:token. It is empty on plain /scan, which is how the page knows which screen to show.
    const { token = '' } = useParams<{ token: string }>()

    // errorMessage: problems with a scan or lookup. cameraError: the camera itself could not start.
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [cameraError, setCameraError] = useState<string | null>(null)

    // When the address has a token, look up its game and go to that game's page.
    useEffect(() =>
    {
        if (!token) return

        // Lets us ignore the answer if the user has already left the page.
        let isCurrent = true

        async function openGame()
        {
            setErrorMessage(null)

            try
            {
                const accessToken = await getAccessTokenSilently({
                    authorizationParams: { audience: apiAudience, scope: 'read:games' },
                })
                const game = await fetchScannedGame(token, accessToken)

                // replace: true swaps this page out of the browser history, so Back does not return to a "Opening..." screen.

                if (isCurrent)
                {
                    navigate(`/collection/${game.id}`, { replace: true })
                }
            }
            catch (error)
            {
                if (isCurrent)
                {
                    setErrorMessage(error instanceof Error ? error.message : 'Could not look up this QR code.')
                }
            }
        }

        void openGame()

        // Cleanup: runs when the page closes or the token changes, so a late answer cannot move the user.
        return () =>
        {
            isCurrent = false
        }
    }, [token, getAccessTokenSilently, navigate])

    // Go to the lookup address for scanned text, or say that it is not one of our labels.
    function handleScannedText(scannedText: string)
    {
        const scannedToken = tokenFromScannedText(scannedText)

        if (!scannedToken)
        {
            setErrorMessage('That is not a Track My Board Games QR code.')
            return
        }

        navigate(`/scan/${scannedToken}`)
    }

    // The scanner library gives a list of everything it saw in the frame; we only use the first code.
    function handleCameraScan(codes: IDetectedBarcode[])
    {
        if (codes.length > 0)
        {
            handleScannedText(codes[0].rawValue)
        }
    }

    // Read a QR code from a photo the user chose (a fallback when the camera is not available).
    async function handlePhotoChosen(event: ChangeEvent<HTMLInputElement>)
    {
        const input = event.target
        const file = input.files?.[0]
        input.value = ''
        if (!file) return

        setErrorMessage(null)

        try
        {
            // BarcodeDetector is the browser's built-in code reader; this import supplies it for browsers that lack it.
            const detector = new BarcodeDetector({ formats: ['qr_code'] })
            const bitmap = await createImageBitmap(file)
            const codes = await detector.detect(bitmap)
            bitmap.close()

            if (codes.length === 0)
            {
                setErrorMessage('No QR code was found in that photo. Try a clearer, closer picture.')
                return
            }

            handleScannedText(codes[0].rawValue)
        }
        catch
        {
            setErrorMessage('That file could not be read as a picture.')
        }
    }

    // A token in the address means we are looking a game up, not showing the camera.
    if (token)
    {
        return (
            <AppLayout>
                <section className="mx-auto max-w-xl py-10 sm:py-14">
                    {errorMessage ? (
                        <>
                            <p className="alert alert-error" role="alert">{errorMessage}</p>
                            <div className="mt-5 flex gap-3">
                                <Link className="btn btn-primary btn-sm" to="/scan">Scan again</Link>
                                <Link className="btn btn-ghost btn-sm" to="/collection">Back to collection</Link>
                            </div>
                        </>
                    ) : (
                        <p className="text-sm text-base-content/70" role="status">Opening your game...</p>
                    )}
                </section>
            </AppLayout>
        )
    }

    // No token in the address: show the camera screen.
    return (
        <AppLayout>
            <section className="mx-auto max-w-xl py-10 sm:py-14">
                <h1 className="font-serif text-4xl leading-tight text-base-content">Scan a game</h1>
                <p className="mt-3 text-sm text-base-content/70">Point your camera at a game's QR code sticker.</p>

                {/* If the camera failed, show why instead of an empty black box; the photo button below still works. */}
                {cameraError ? (
                    <p className="alert alert-warning mt-6" role="alert">{cameraError}</p>
                ) : (
                    <div className="mt-6 overflow-hidden rounded-box border border-base-300">
                        <Scanner
                            formats={['qr_code']}
                            onScan={handleCameraScan}
                            onError={error => setCameraError(cameraMessage(error))}
                        />
                    </div>
                )}

                {errorMessage && <p className="alert alert-error mt-4" role="alert">{errorMessage}</p>}

                <div className="mt-5 flex flex-wrap items-center gap-3">
                    <label className="btn btn-outline btn-sm">
                        Choose a photo instead
                        <input className="hidden" type="file" accept="image/*" onChange={handlePhotoChosen} />
                    </label>
                    <Link className="btn btn-ghost btn-sm" to="/collection">Back to collection</Link>
                </div>
            </section>
        </AppLayout>
    )
}

export default ScanPage
