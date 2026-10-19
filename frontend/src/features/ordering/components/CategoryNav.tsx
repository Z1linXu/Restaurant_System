import type { MenuCategory } from '../../../types/ordering'
import { useStorePresentation } from '../../store/useStorePresentation'

interface CategoryNavProps {
  categories: MenuCategory[]
  activeCategoryId: string
  onSelect: (categoryId: string) => void
  compact?: boolean
}

export function CategoryNav({ categories, activeCategoryId, onSelect, compact = false }: CategoryNavProps) {
  const chineseFirst = useStorePresentation().locale.toLowerCase().startsWith('zh')
  return (
    <div className={compact ? 'space-y-2.5' : 'space-y-3'}>
      {categories.map((category) => {
        const isActive = category.id === activeCategoryId

        return (
          <button
            key={category.id}
            type="button"
            onClick={() => onSelect(category.id)}
            className={`flex w-full items-center text-left transition ${
              isActive
                ? 'bg-[var(--surface-container-lowest)] text-[var(--primary)] shadow-[0_14px_30px_rgba(97,0,0,0.10)]'
                : 'bg-transparent text-[rgba(53,43,38,0.72)] hover:bg-[rgba(26,28,25,0.04)]'
            } ${compact ? 'min-h-[3.6rem] rounded-[18px] px-4' : 'min-h-16 rounded-[22px] px-5'}`}
          >
            <div className="space-y-1">
              <div className={compact ? 'text-[1.02rem] font-bold' : 'text-[1.2rem] font-bold'}>{chineseFirst ? category.labelZh || category.labelEn : category.labelEn || category.labelZh}</div>
              <div className={compact ? 'text-[0.8rem] font-medium text-[var(--muted)]' : 'text-sm font-medium text-[var(--muted)]'}>{chineseFirst ? category.labelEn : category.labelZh}</div>
            </div>
          </button>
        )
      })}
    </div>
  )
}
