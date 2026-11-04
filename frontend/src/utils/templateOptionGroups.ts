import type { TemplateOptionGroup } from '../services/templatePlatformService'

export const TEMPLATE_GROUP_LABELS: Record<TemplateOptionGroup['kind'], string> = { SPICY_LEVEL: 'Spicy / 辣度', ADD_ON: 'Add-on / 加料', REMOVE: 'Remove / 去料', SIZE: 'Size / 份量', CUSTOM: 'Custom / 自定义', COMBO: 'Combo / 套餐（沿用已有配置）' }

export function newTemplateGroup(kind: TemplateOptionGroup['kind'] = 'CUSTOM'): TemplateOptionGroup {
  return { group_instance_code: crypto.randomUUID(), name: kind === 'CUSTOM' ? 'New group / 新选项组' : TEMPLATE_GROUP_LABELS[kind], kind,
    selection_mode: kind === 'ADD_ON' || kind === 'REMOVE' ? 'MULTIPLE' : 'SINGLE', required: false,
    sort_order: 0, is_active: true, default_option_ids: [], options: [] }
}

export function copyTemplateGroup(source: TemplateOptionGroup): TemplateOptionGroup {
  return { ...source, group_instance_code: crypto.randomUUID(), source_library_code: source.group_instance_code, default_option_ids: [],
    name_translations: source.name_translations ? { ...source.name_translations } : undefined,
    options: source.options.map((option) => ({ ...option, id: undefined, parent_option_id: null,
      is_default: Boolean(option.is_default || (option.id != null && source.default_option_ids.includes(option.id))) })) }
}
