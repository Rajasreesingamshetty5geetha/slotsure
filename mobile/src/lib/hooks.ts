import { DependencyList, useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from './api';

export type Resource<T> = { data: T | null; error: string; loading: boolean; reload: () => Promise<void> };

/** Loads one thing, keeps its own error, and ignores answers that arrive after a newer request. */
export function useResource<T>(load: () => Promise<T>, deps: DependencyList): Resource<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const latest = useRef(0);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(load, deps);

  const reload = useCallback(async () => {
    const id = ++latest.current;
    setLoading(true);
    setError('');
    try {
      const result = await run();
      if (id === latest.current) setData(result);
    } catch (e) {
      if (id === latest.current) {
        setData(null);
        setError(e instanceof ApiError ? e.message : 'Something went wrong.');
      }
    } finally {
      if (id === latest.current) setLoading(false);
    }
  }, [run]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { data, error, loading, reload };
}
