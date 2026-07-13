import { ApiRequestError } from '../services/apiClient'

export function canUseOfflineSnapshot(error: unknown) {
  return error instanceof ApiRequestError
    && (error.status === 0 || error.status >= 500)
}
