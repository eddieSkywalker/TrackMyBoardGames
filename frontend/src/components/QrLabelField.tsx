import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { QRCodeSVG } from 'qrcode.react'
import { apiAudience } from '../api/apiHelpers'
import { buildScanUrl, createLabel, fetchLabels, findActiveLabel, revokeLabel, type QrLabel } from '../api/labelApi'

type QrLabelFieldProps = {
    gameId: string
    disabled: boolean
}

// The "QR code" section of the edit form: shows the game's current QR code with Generate / Delete buttons.
// Unlike the cover photo, these buttons act right away (the label is saved on the server immediately).
function QrLabelField({ gameId, disabled }: QrLabelFieldProps)
{
    const { getAccessTokenSilently } = useAuth0()
    const [label, setLabel] = useState<QrLabel | null>(null)
    const [isWorking, setIsWorking] = useState(false)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)

    // When the form opens, look up whether this game already has a working QR code.
    useEffect(() =>
    {
        let isCurrent = true

        async function loadLabel()
        {
            try
            {
                const accessToken = await getAccessTokenSilently({
                    authorizationParams: { audience: apiAudience, scope: 'read:games' },
                })
                const labels = await fetchLabels(accessToken)

                if (isCurrent)
                {
                    setLabel(findActiveLabel(labels, gameId))
                }
            }
            catch (error)
            {
                if (isCurrent)
                {
                    setErrorMessage(error instanceof Error ? error.message : 'Could not load the QR code.')
                }
            }
        }

        void loadLabel()

        return () =>
        {
            isCurrent = false
        }
    }, [gameId, getAccessTokenSilently])

    async function handleGenerate()
    {
        setIsWorking(true)
        setErrorMessage(null)

        try
        {
            const accessToken = await getAccessTokenSilently({
                authorizationParams: { audience: apiAudience, scope: 'write:games' },
            })
            setLabel(await createLabel(gameId, accessToken))
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not generate the QR code.')
        }
        finally
        {
            setIsWorking(false)
        }
    }

    async function handleDelete()
    {
        if (!label) return
        if (!window.confirm('Delete this QR code? A sticker already printed with it will stop working.')) return

        setIsWorking(true)
        setErrorMessage(null)

        try
        {
            const accessToken = await getAccessTokenSilently({
                authorizationParams: { audience: apiAudience, scope: 'write:games' },
            })
            await revokeLabel(gameId, label.id, accessToken)
            setLabel(null)
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not delete the QR code.')
        }
        finally
        {
            setIsWorking(false)
        }
    }

    const isDisabled = disabled || isWorking

    return (
        <div className="grid gap-2">
            <span className="text-sm font-semibold">QR code</span>
            <div className="flex flex-wrap items-center gap-4">
                {label ? (
                    // White padding around the code is required for scanners to read it, even in dark mode.
                    <div className="rounded bg-white p-2">
                        <QRCodeSVG value={buildScanUrl(label.token)} size={80} level="M" />
                    </div>
                ) : (
                    <div className="grid size-24 place-items-center rounded bg-base-300 text-xs font-semibold uppercase text-base-content/60">
                        No QR code
                    </div>
                )}
                <div className="grid gap-2">
                    {label ? (
                        <button className="btn btn-outline btn-sm w-fit" type="button" onClick={handleDelete} disabled={isDisabled}>
                            Delete QR Code
                        </button>
                    ) : (
                        <button className="btn btn-outline btn-sm w-fit" type="button" onClick={handleGenerate} disabled={isDisabled}>
                            {isWorking ? 'Generating...' : 'Generate QR Code'}
                        </button>
                    )}
                </div>
            </div>
            {errorMessage && <p className="text-xs text-error" role="alert">{errorMessage}</p>}
        </div>
    )
}

export default QrLabelField
