/** Additive, secret-free effective configuration returned for one Store. */
export interface StoreSafeSummary {
  schema_version: 1
  config_revision: number
  brand_name: string
  locale: string
  currency: 'CAD'
  timezone: string
  tax_policy_reference: string
  profile_code: string
  compatibility_mode: 'NEUTRAL' | 'PROVISIONAL_COMPATIBILITY'
}
