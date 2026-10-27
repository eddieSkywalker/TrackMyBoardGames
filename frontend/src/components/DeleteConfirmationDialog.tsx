import { useEffect, useId, useRef, useState } from 'react'

type DeleteConfirmationDialogProps = {
    title: string
    description: string
    confirmationValue: string
    confirmLabel: string
    onConfirm: () => Promise<void>
    onCancel: () => void
}

// Shared game/play confirmation; the parent supplies the required text and the delete request.
function DeleteConfirmationDialog({
    title, description, confirmationValue, confirmLabel, onConfirm, onCancel,
}: DeleteConfirmationDialogProps)
{
    const dialogRef = useRef<HTMLDialogElement>(null)
    const cancelRef = useRef<HTMLButtonElement>(null)
    // Lock immediately, before React re-renders, to prevent rapid duplicate requests.
    const requestInFlight = useRef(false)
    const id = useId()
    const [input, setInput] = useState('')
    const [isDeleting, setIsDeleting] = useState(false)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)

    // Open with Cancel focused, then restore the previous focus when the dialog is removed.
    useEffect(() =>
    {
        const dialog = dialogRef.current
        const previousFocus = document.activeElement
        dialog?.showModal()
        cancelRef.current?.focus()
        return () =>
        {
            dialog?.close()
            if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus()
        }
    }, [])

    // Only close after a successful delete; failures stay visible so the user can retry.
    async function confirmDeletion()
    {
        if (input !== confirmationValue || requestInFlight.current) return
        requestInFlight.current = true
        setIsDeleting(true)
        setErrorMessage(null)
        try
        {
            await onConfirm()
            onCancel()
        }
        catch (error)
        {
            setErrorMessage(error instanceof Error ? error.message : 'Could not delete this record.')
        }
        finally
        {
            requestInFlight.current = false
            setIsDeleting(false)
        }
    }

    // Handle both keyboard and browser cancellation, blocking dismissal while deletion is pending.
    return (
        <dialog
            ref={dialogRef}
            className="modal"
            aria-labelledby={`${id}-title`}
            aria-describedby={`${id}-description`}
            onKeyDown={event =>
            {
                if (event.key !== 'Escape') return
                event.preventDefault()
                if (!requestInFlight.current) onCancel()
            }}
            onCancel={event =>
            {
                event.preventDefault()
                if (!requestInFlight.current) onCancel()
            }}
        >
            <div className="modal-box max-h-[85svh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto border border-base-300 bg-base-100 text-base-content">
                <h2 id={`${id}-title`} className="text-xl font-bold">{title}</h2>
                <p id={`${id}-description`} className="mt-3 text-sm">{description}</p>
                <p id={`${id}-instruction`} className="mt-4 text-sm">
                    Please type <strong className="select-text break-words">{confirmationValue}</strong> to confirm.
                </p>
                <label className="mt-4 grid gap-2 text-sm font-semibold">
                    Confirmation
                    <input
                        className="input w-full bg-base-100 font-normal"
                        value={input}
                        onChange={event => setInput(event.target.value)}
                        onKeyDown={event =>
                        {
                            // Matching text enables Delete, but Enter here must not activate it.
                            if (event.key === 'Enter') event.preventDefault()
                        }}
                        aria-describedby={`${id}-instruction`}
                        autoComplete="off"
                        autoCapitalize="none"
                        spellCheck={false}
                        disabled={isDeleting}
                    />
                </label>
                {errorMessage && <p className="alert alert-error mt-4 text-sm" role="alert">{errorMessage}</p>}
                {isDeleting && <p className="mt-4 text-sm" role="status">Deleting...</p>}
                <div className="modal-action flex-wrap">
                    <button ref={cancelRef} className="btn btn-ghost" type="button" disabled={isDeleting} onClick={onCancel}>
                        Cancel
                    </button>
                    <button
                        className="btn btn-error"
                        type="button"
                        disabled={input !== confirmationValue || isDeleting}
                        onClick={() => void confirmDeletion()}
                    >
                        {isDeleting ? 'Deleting...' : confirmLabel}
                    </button>
                </div>
            </div>
        </dialog>
    )
}

export default DeleteConfirmationDialog
