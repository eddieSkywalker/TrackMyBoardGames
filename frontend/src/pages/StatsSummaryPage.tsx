import { useCurrentCollection } from '../hooks/useCurrentCollection'
import TrackView from '../components/track/TrackView'

/** Starts Track at the viewed shelf while keeping its temporary All Collections choice separate. */
function StatsSummaryPage()
{
    // Start with the shelf the user was viewing on the Collection page.
    const { currentCollectionId } = useCurrentCollection()

    // Returning from another shelf starts a fresh Track view; account changes reset the shared provider too.
    return <TrackView key={currentCollectionId ?? 'default'} initialCollectionId={currentCollectionId} />
}

export default StatsSummaryPage
