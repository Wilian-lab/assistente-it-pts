import { useQuery } from '@tanstack/react-query'

import { useAuth } from '../hooks/useAuth'
import { ptsService } from '../services/pts/ptsService'

export function usePtsProducts() {
  const { user } = useAuth()
  const activeSector = String(user?.setorAtivo ?? '').trim()

  return useQuery({
    queryKey: ['pts-products', activeSector],
    queryFn: ptsService.getProducts,
    staleTime: 30_000,
    refetchOnWindowFocus: true,
    refetchInterval: 30_000,
    enabled: Boolean(activeSector),
  })
}

export function usePtsItems(product: string) {
  const { user } = useAuth()
  const activeSector = String(user?.setorAtivo ?? '').trim()

  return useQuery({
    queryKey: ['pts-items', activeSector, product],
    queryFn: () => ptsService.getItems(product),
    staleTime: 30_000,
    refetchOnWindowFocus: true,
    refetchInterval: 30_000,
    enabled: Boolean(activeSector) && !!product,
  })
}

export function usePtsData(product: string, item?: string) {
  const { user } = useAuth()
  const activeSector = String(user?.setorAtivo ?? '').trim()

  return useQuery({
    queryKey: ['pts-data', activeSector, product, item],
    queryFn: () => ptsService.getData(product, item),
    enabled: Boolean(activeSector) && !!product,
    staleTime: 30_000,
    refetchOnWindowFocus: true,
    refetchInterval: 30_000,
  })
}
