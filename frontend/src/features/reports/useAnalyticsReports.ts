import { useEffect, useMemo, useState } from 'react'
import {
  fetchAnalyticsSummaries,
  type AnalyticsReportRange,
  type AnalyticsSummaryResponse,
} from '../../services/analyticsReportService'
import { fetchPlatformOverview, type PlatformAdminOverview } from '../../services/platformAdminService'
import { toPreviousReportQuery, toReportQuery } from './reportUtils'

export interface ReportStoreOption {
  id: string
  label: string
}

export function useAnalyticsReports() {
  const [overview, setOverview] = useState<PlatformAdminOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedStoreId, setSelectedStoreId] = useState('ALL')
  const [selectedRange, setSelectedRange] = useState<AnalyticsReportRange>('today')
  const [compareEnabled, setCompareEnabled] = useState(true)
  const [customStartDate, setCustomStartDate] = useState('')
  const [customEndDate, setCustomEndDate] = useState('')
  const [currentSummary, setCurrentSummary] = useState<AnalyticsSummaryResponse | null>(null)
  const [previousSummary, setPreviousSummary] = useState<AnalyticsSummaryResponse | null>(null)

  useEffect(() => {
    const loadOverview = async () => {
      setError(null)
      try {
        const nextOverview = await fetchPlatformOverview(1)
        setOverview(nextOverview)
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : 'Failed to load report scope')
      }
    }

    void loadOverview()
  }, [])

  const organizationId = useMemo(
    () => Number(overview?.organizations?.[0]?.id ?? 1),
    [overview],
  )

  const stores = useMemo<ReportStoreOption[]>(
    () => [
      { id: 'ALL', label: 'All Stores' },
      ...(overview?.stores ?? []).map((store) => ({
        id: String(store.id),
        label: String(store.name ?? `Store ${store.id}`),
      })),
    ],
    [overview],
  )

  useEffect(() => {
    if (selectedRange !== 'custom' || (customStartDate && customEndDate)) {
      return
    }
    const today = new Date().toISOString().slice(0, 10)
    setCustomStartDate(today)
    setCustomEndDate(today)
  }, [selectedRange, customEndDate, customStartDate])

  useEffect(() => {
    if (!overview) {
      return
    }
    if (selectedRange === 'custom' && (!customStartDate || !customEndDate)) {
      return
    }

    const loadSummaries = async () => {
      setLoading(true)
      setError(null)
      try {
        const currentQuery = toReportQuery({
          organizationId,
          storeId: selectedStoreId,
          range: selectedRange,
          customStartDate,
          customEndDate,
        })
        const previousQuery = toPreviousReportQuery({
          organizationId,
          storeId: selectedStoreId,
          range: selectedRange,
          customStartDate,
          customEndDate,
        })

        const [current, previous] = await Promise.all([
          fetchAnalyticsSummaries(currentQuery),
          compareEnabled ? fetchAnalyticsSummaries(previousQuery) : Promise.resolve(null),
        ])
        setCurrentSummary(current)
        setPreviousSummary(previous)
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : 'Failed to load analytics summaries')
        setCurrentSummary(null)
        setPreviousSummary(null)
      } finally {
        setLoading(false)
      }
    }

    void loadSummaries()
  }, [compareEnabled, customEndDate, customStartDate, organizationId, overview, selectedRange, selectedStoreId])

  return {
    organizationId,
    stores,
    selectedStoreId,
    setSelectedStoreId,
    selectedRange,
    setSelectedRange,
    compareEnabled,
    setCompareEnabled,
    customStartDate,
    setCustomStartDate,
    customEndDate,
    setCustomEndDate,
    currentSummary,
    previousSummary,
    loading,
    error,
  }
}
