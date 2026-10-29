import { useEffect, useId, useRef } from 'react'
import { init, use as registerECharts, type ComposeOption } from 'echarts/core'
import { HeatmapChart, type HeatmapSeriesOption } from 'echarts/charts'
import {
    CalendarComponent, TooltipComponent, VisualMapComponent,
    type CalendarComponentOption, type TooltipComponentOption, type VisualMapComponentOption,
} from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { MonthlyHeatmap } from '../api/statisticsApi'

// Load only the chart features needed for the monthly heatmap.
registerECharts([HeatmapChart, CalendarComponent, TooltipComponent, VisualMapComponent, SVGRenderer])

type HeatmapOption = ComposeOption<HeatmapSeriesOption | CalendarComponentOption| TooltipComponentOption | VisualMapComponentOption>

/** Shows the current month as compact weekday rows and week columns, rather than a full-size calendar. */
function MonthlyPlayHeatmap({ heatmap }: { heatmap: MonthlyHeatmap })
{
    // Prepare the month heading and remember the page element where ECharts will draw.
    const titleId = useId()
    const chartElement = useRef<HTMLDivElement>(null)
    const firstDate = new Date(`${heatmap.month}-01T00:00:00Z`)
    const monthTitle = firstDate.toLocaleDateString(undefined, { month: 'long', year: 'numeric', timeZone: 'UTC' })

    // Draw after React has placed the chart element on the page, and rebuild when the saved data changes.
    useEffect(() =>
    {
        const element = chartElement.current
        // Pending statistics have no daily data yet, so there is no chart to draw.
        if (!element || heatmap.days === null)
        {
            return
        }

        // Create an ECharts instance for this element using scalable SVG graphics.
        const chart = init(element, undefined, { renderer: 'svg' })
        const days = heatmap.days

        /** Configure the heatmap using saved daily counts and the app's current theme colors. */
        function drawChart()
        {
            // Read the active colors each time so labels and borders follow light/dark mode.
            const styles = getComputedStyle(chart.getDom())
            const textColor = styles.getPropertyValue('--color-base-content').trim()
            const borderColor = styles.getPropertyValue('--color-base-300').trim()
            const backgroundColor = styles.getPropertyValue('--color-base-100').trim()
            const option: HeatmapOption = {
                animation: false,
                // Show the date and recorded play count when a user points to a day.
                tooltip: {
                    confine: true,
                    backgroundColor,
                    borderColor,
                    textStyle: { color: textColor },
                    formatter: parameter =>
                    {
                        const item = Array.isArray(parameter) ? parameter[0] : parameter
                        const value = item.value
                        if (!Array.isArray(value) || typeof value[0] !== 'string' || typeof value[1] !== 'number')
                        {
                            return ''
                        }
                        return `${value[0]}: ${value[1]} ${value[1] === 1 ? 'play' : 'plays'}`
                    },
                },
                // Match play-count ranges to blue shades and show the same ranges in the legend.
                visualMap: {
                    type: 'piecewise',
                    orient: 'horizontal',
                    left: 'center',
                    top: 10,
                    itemWidth: 12,
                    itemHeight: 12,
                    itemGap: 12,
                    textStyle: { color: textColor, fontSize: 11 },
                    selectedMode: false,
                    pieces: [
                        { value: 0, label: '0', color: '#e0e7ff' },
                        { value: 1, label: '1', color: '#b3c2fa' },
                        { min: 2, max: 3, label: '2-3', color: '#859cf0' },
                        { min: 4, max: 6, label: '4-6', color: '#617ee6' },
                        { min: 7, label: '7+', color: '#405bd4' },
                    ],
                },
                // Limit the chart to this month, with weekdays down the side and weeks across.
                calendar: {
                    range: heatmap.month,
                    orient: 'horizontal',
                    top: 65,
                    left: 'center',
                    cellSize: [26, 20],
                    yearLabel: { show: false },
                    monthLabel: { show: false },
                    dayLabel: {
                        firstDay: 0,
                        nameMap: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
                        color: textColor,
                        fontSize: 11,
                        margin: 8,
                    },
                    splitLine: { lineStyle: { color: borderColor, width: 1 } },
                    itemStyle: { color: backgroundColor, borderColor, borderWidth: 0.5 },
                },
                // Give ECharts each saved date/count pair so it can color the correct day.
                series: [{
                    type: 'heatmap',
                    coordinateSystem: 'calendar',
                    data: days.map(day => [day.date, day.playCount]),
                    itemStyle: { borderColor: backgroundColor, borderWidth: 1 },
                    emphasis: { itemStyle: { borderColor: textColor, borderWidth: 1 } },
                }],
            }
            chart.setOption(option)
        }

        // Draw once, then keep the chart fitted to its container and updated when the theme changes.
        drawChart()
        const resizeObserver = new ResizeObserver(() => chart.resize())
        resizeObserver.observe(element)
        const themeObserver = new MutationObserver(drawChart)
        themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

        return () =>
        {
            // Release the chart and its observers when leaving this view or replacing its data.
            resizeObserver.disconnect()
            themeObserver.disconnect()
            chart.dispose()
        }
    }, [heatmap])

    return (
        <section className="mt-6 rounded-box border border-base-300 bg-base-100 p-4 shadow-sm sm:p-6"
            aria-labelledby={titleId}>
            <h2 id={titleId} className="font-serif text-2xl font-bold">{monthTitle}</h2>
            <p className="mt-1 text-sm text-base-content/70">Recorded plays by day ({heatmap.timezone})</p>

            {/* Keep uncalculated statistics separate from a calculated month with no plays. */}
            {heatmap.days === null ? (
                <p className="mt-6 text-sm" role="status">Statistics have not been calculated yet. Check back after the background update finishes.</p>
            ) : (
                <>
                    <div ref={chartElement} className="mt-4 h-56 w-full" role="img"
                        aria-label={`${monthTitle} play heatmap. Rows show Sunday through Saturday; columns show weeks. Darker blue means more plays. Daily counts are available in the table below.`} />
                    {/* Offer the same daily counts without requiring color recognition or hovering over cells. */}
                    <details className="mt-2 text-sm">
                        <summary className="cursor-pointer">Daily play counts</summary>
                        <table className="table table-sm mt-2">
                            <caption className="sr-only">{monthTitle} daily plays</caption>
                            <thead><tr><th scope="col">Date</th><th scope="col">Plays</th></tr></thead>
                            <tbody>
                                {heatmap.days.map(day => (
                                    <tr key={day.date}><th scope="row">{day.date}</th><td>{day.playCount}</td></tr>
                                ))}
                            </tbody>
                        </table>
                    </details>
                    {heatmap.days.every(day => day.playCount === 0) && (
                        <p className="mt-4 text-sm text-base-content/70">No plays recorded this month.</p>
                    )}
                </>
            )}
        </section>
    )
}

export default MonthlyPlayHeatmap
