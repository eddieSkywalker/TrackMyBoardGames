import AppLayout from '../components/AppLayout'

function StatsSummaryPage()
{
    return (
        <AppLayout>
            <section className="mx-auto mt-6 max-w-6xl" aria-labelledby="stats-summary-title">
                <h1 id="stats-summary-title" className="text-2xl font-bold">Collection insights</h1>
                <p className="mt-2 text-base-content/70">
                    Charts and visual summaries of your collection will appear here.
                </p>
            </section>
        </AppLayout>
    )
}

export default StatsSummaryPage
