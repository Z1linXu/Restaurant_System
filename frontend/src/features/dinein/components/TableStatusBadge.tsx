import { Badge } from '../../../components/ui/Badge'
import type { TableStatus } from '../../../types/dinein'

interface TableStatusBadgeProps {
  status: TableStatus
}

const labelMap: Record<TableStatus, string> = {
  available: 'Available',
  occupied: 'Occupied',
  alert: 'Alert',
}

const variantMap: Record<TableStatus, 'neutral' | 'accent' | 'warm' | 'success'> = {
  available: 'success',
  occupied: 'neutral',
  alert: 'warm',
}

export function TableStatusBadge({ status }: TableStatusBadgeProps) {
  return <Badge variant={variantMap[status]}>{labelMap[status]}</Badge>
}
