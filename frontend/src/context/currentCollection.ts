import { createContext, type Dispatch, type SetStateAction } from 'react'

// Defines the information shared between Collection and Track: the viewed shelf's ID and a function to change it.
// A null ID means no shelf has been selected yet; this does not change the account's saved default.
type CurrentCollectionState = {
    currentCollectionId: string | null
    setCurrentCollectionId: Dispatch<SetStateAction<string | null>>
}

// Creates the shared connection, not the selection itself. CurrentCollectionProvider holds the selection,
// and the useCurrentCollection hook lets pages access it. A null context means no provider surrounds the page.
export const CurrentCollectionContext = createContext<CurrentCollectionState | null>(null)
