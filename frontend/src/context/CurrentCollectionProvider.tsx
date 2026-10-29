import { useState, type ReactNode } from 'react'
import { CurrentCollectionContext } from './currentCollection'

/** Remembers the viewed shelf while navigating pages; a new account starts with fresh state. */
function CurrentCollectionProvider({ children }: { children: ReactNode })
{
    // Remember the viewed collection and provide a function for changing it.
    const [currentCollectionId, setCurrentCollectionId] = useState<string | null>(null)

    const collectionState = {
        currentCollectionId: currentCollectionId,
        setCurrentCollectionId: setCurrentCollectionId,
    }

    // Make this shared state available to the pages rendered inside this provider.
    return (
        <CurrentCollectionContext value={collectionState}>
            {children}
        </CurrentCollectionContext>
    )
}

export default CurrentCollectionProvider
