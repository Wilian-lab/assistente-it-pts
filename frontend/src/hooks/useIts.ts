import { useQuery } from '@tanstack/react-query'

import { useAuth } from '../hooks/useAuth'
import { itService } from '../services/its/itService'

export function useIts() {
  const { user } = useAuth()
  const activeSector = String(user?.setorAtivo ?? '').trim()

  return useQuery({
    queryKey: ['its', activeSector],
    queryFn: itService.list,
    staleTime: 15_000,
    refetchOnWindowFocus: true,
    refetchInterval: 20_000,
    enabled: Boolean(activeSector),
  })
}
