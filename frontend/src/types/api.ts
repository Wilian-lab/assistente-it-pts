export interface ApiErrorResponse {
  message: string
  field: string | null
  status: number
  timestamp: string
}
