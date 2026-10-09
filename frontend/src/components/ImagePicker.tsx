import { useState, type ChangeEvent } from 'react'
import { resizeImage } from '../api/imageApi'

type ImagePickerProps = {
    // Longest side, in pixels, the picture is shrunk to before it is handed back.
    maxSize: number
    disabled?: boolean
    // Called with the ready-to-upload picture once the user has chosen one.
    onPick: (image: Blob) => void
}

// Two buttons for getting a picture:
//  - "Choose photo": on a computer this opens the file browser; on a phone it offers the photo library or camera.
//  - "Take photo": only appears on touch screens and opens the camera straight away.
function ImagePicker({ maxSize, disabled = false, onPick }: ImagePickerProps)
{
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [isPreparing, setIsPreparing] = useState(false)

    async function handleFileChosen(event: ChangeEvent<HTMLInputElement>)
    {
        const input = event.target
        const file = input.files?.[0]

        // Clear the input so choosing the same file again still counts as a change.
        input.value = ''
        if (!file) return

        setErrorMessage(null)
        setIsPreparing(true)

        try
        {
            onPick(await resizeImage(file, maxSize))
        }
        catch
        {
            setErrorMessage('That file could not be read as a picture. Try a JPEG, PNG, or WebP image.')
        }
        finally
        {
            setIsPreparing(false)
        }
    }

    const isDisabled = disabled || isPreparing

    return (
        <div>
            <div className="flex flex-wrap items-center gap-2">
                <label className={`btn btn-outline btn-sm ${isDisabled ? 'btn-disabled' : ''}`}>
                    {isPreparing ? 'Preparing...' : 'Choose photo'}
                    <input
                        className="hidden"
                        type="file"
                        accept="image/*"
                        disabled={isDisabled}
                        onChange={handleFileChosen}
                    />
                </label>
                {/* "capture" asks a phone to open its camera; "pointer-coarse" means a touch screen is the main input. */}
                <label className={`btn btn-outline btn-sm hidden pointer-coarse:inline-flex ${isDisabled ? 'btn-disabled' : ''}`}>
                    Take photo
                    <input
                        className="hidden"
                        type="file"
                        accept="image/*"
                        capture="environment"
                        disabled={isDisabled}
                        onChange={handleFileChosen}
                    />
                </label>
            </div>
            {errorMessage && <p className="mt-2 text-xs text-error" role="alert">{errorMessage}</p>}
        </div>
    )
}

export default ImagePicker
