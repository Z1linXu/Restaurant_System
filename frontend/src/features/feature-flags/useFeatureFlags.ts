import { featureConfig, isFeatureEnabled, type FeaturePackage } from './featureConfig'

export function useFeatureFlags() {
  return {
    features: featureConfig,
    isEnabled: (feature: FeaturePackage) => isFeatureEnabled(feature),
  }
}
