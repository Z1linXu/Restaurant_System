/** Stable per deployment, never per build or UI locale. Preserve legacy data in its original database. */
export function normalizeDeploymentNamespace(value: string | undefined): string {
  if (!value) return ''
  if (!/^[a-z0-9][a-z0-9-]{0,63}$/.test(value)) throw new Error('Invalid deployment namespace')
  return value
}
export const deploymentNamespace = normalizeDeploymentNamespace(
  typeof __APP_DEPLOYMENT_NAMESPACE__ === 'string' ? __APP_DEPLOYMENT_NAMESPACE__ : '',
)
export function deploymentStorageKey(key: string, namespace = deploymentNamespace): string {
  return namespace ? `${normalizeDeploymentNamespace(namespace)}:${key}` : key
}
