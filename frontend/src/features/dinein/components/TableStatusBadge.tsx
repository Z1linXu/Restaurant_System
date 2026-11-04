import { useLocale } from '../../../i18n/useLocale'
import { Badge } from '../../../components/ui/Badge'
import type { TableStatus } from '../../../types/dinein'

interface TableStatusBadgeProps {
  status: TableStatus
}

const variantMap: Record<TableStatus, 'neutral' | 'accent' | 'warm' | 'success'> = {
  available: 'success',
  occupied: 'neutral',
  alert: 'warm',
}

export function TableStatusBadge({ status }: TableStatusBadgeProps) {
  const { t } = useLocale()
  return <Badge variant={variantMap[status]}>{t(`tables.${status}`)}</Badge>
}
