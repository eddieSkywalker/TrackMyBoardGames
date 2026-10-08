// Shared frontend shapes for a saved game from the API and values entered into its form.
export type BoardGame = {
    id: string
    name: string
    minPlayers: number | null
    maxPlayers: number | null
    bestPlayers: string | null
    playTimeMinutes: number | null
    notes: string | null
    createdAt: string
    updatedAt: string
}

export type GamePlay = {
    id: string
    gameId: string
    playedOn: string
    durationMinutes: number | null
    playerCount: number | null
    notes: string | null
    createdAt: string
    updatedAt: string
}

export type PlayFormValues = {
    playedOn: string
    durationMinutes: string
    playerCount: string
    notes: string
}

export type GameFormValues = {
    name: string
    minPlayers: string
    maxPlayers: string
    bestPlayers: string
    playTimeMinutes: string
    notes: string
}