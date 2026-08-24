const NOODLE_TYPE_DISPLAY_SUFFIX_BY_CODE: Record<string, string> = {
  noodle_capillary: '（1）',
  noodle_thin: '（2）',
  noodle_sanxi: '（3）',
  noodle_erxi: '（4）',
  noodle_leek_leaf: '（5）',
  noodle_wide: '（6）',
  noodle_extra_wide: '（7）',
}

export function formatNoodleTypeDisplayLabel(labelZh: string, optionCode?: string | null) {
  const suffix = optionCode ? NOODLE_TYPE_DISPLAY_SUFFIX_BY_CODE[optionCode] : undefined
  return suffix ? `${labelZh}${suffix}` : labelZh
}
