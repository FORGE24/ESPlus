// ═══════════════════════════════════════════════════════════
// ES+ Panel — Data fetching hook
// ═══════════════════════════════════════════════════════════

import { useState, useEffect, useCallback, useRef } from 'react'
import { ApiError } from './api'

interface FetchState<T> {
  data: T | null
  loading: boolean
  error: string | null
  refetch: () => void
}

export function useApi<T>(
  fetcher: () => Promise<T>,
  deps: unknown[] = [],
  options: { interval?: number } = {},
): FetchState<T> {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tick, setTick] = useState(0)
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  const refetch = useCallback(() => setTick((t) => t + 1), [])

  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setInterval> | undefined

    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const result = await fetcherRef.current()
        if (active) setData(result)
      } catch (e) {
        if (active) {
          if (e instanceof ApiError && e.status === 401) return
          setError(e instanceof Error ? e.message : String(e))
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    load()

    if (options.interval) {
      timer = setInterval(load, options.interval)
    }

    return () => {
      active = false
      if (timer) clearInterval(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  return { data, loading, error, refetch }
}

/** Execute an API mutation with toast feedback */
export function useMutation() {
  const [loading, setLoading] = useState(false)

  const mutate = useCallback(async <T,>(
    fn: () => Promise<T>,
    opts: {
      successMsg?: string
      errorMsg?: string
      onSuccess?: (data: T) => void
    } = {},
  ): Promise<T | null> => {
    setLoading(true)
    try {
      const result = await fn()
      if (opts.onSuccess) opts.onSuccess(result)
      return result
    } catch (e) {
      console.error(opts.errorMsg || '操作失败', e)
      throw e
    } finally {
      setLoading(false)
    }
  }, [])

  return { loading, mutate }
}
