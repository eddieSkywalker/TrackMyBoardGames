// Shared frontend shapes for a saved game from the API and values entered into its form.
export type BoardGame = {
    id: string
    name: string
    minPlayers: number | null
    maxPlayers: number | null
    playTimeMinutes: number | null
    notes: string | null
    createdAt: string
    updatedAt: string
}

export type GameFormValues = {
    name: string
    minPlayers: string
    maxPlayers: string
    playTimeMinutes: string
    notes: string
}