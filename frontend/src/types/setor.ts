const SETOR_LABEL_ALIASES: Record<string, string> = {
  AGRI_PRODUCTS: 'Agri-Products',
}

export function getSetorLabel(value: string | null | undefined) {
  const code = String(value ?? '').trim().toUpperCase()
  if (!code) return '-'
  if (SETOR_LABEL_ALIASES[code]) return SETOR_LABEL_ALIASES[code]
  return code
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function getSetorBrandName(value: string | null | undefined) {
  const label = getSetorLabel(value)
  return label === '-' ? '' : label
}

export function parseSetorCodes(value: string | null | undefined): string[] {
  return String(value ?? '')
    .split(',')
    .map((part) => part.trim().toUpperCase())
    .filter(Boolean)
}
