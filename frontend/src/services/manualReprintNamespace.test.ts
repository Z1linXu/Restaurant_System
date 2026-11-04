import { beforeEach, expect, it, vi } from 'vitest'
const scope = vi.hoisted(() => ({ namespace: '' }))
vi.mock('../utils/deploymentNamespace', async original => {
  const real = await original<typeof import('../utils/deploymentNamespace')>()
  return { ...real, deploymentStorageKey: (key: string) => real.deploymentStorageKey(key, scope.namespace) }
})
vi.mock('./apiClient', async () => ({ ...await vi.importActual<typeof import('./apiClient')>('./apiClient'), apiRequest: vi.fn(),
  getAccessToken: () => `header.${btoa(JSON.stringify({ user_id: 1, organization_id: 1 }))}.signature` }))
import { ApiRequestError, apiRequest } from './apiClient'
import { manualReprint } from './manualReprintService'
const api=vi.mocked(apiRequest)
const storage=new Map<string,string>()
beforeEach(()=>{scope.namespace='';api.mockReset();storage.clear()
 vi.stubGlobal('localStorage',{getItem:(k:string)=>storage.get(k)??null,setItem:(k:string,v:string)=>storage.set(k,v),removeItem:(k:string)=>storage.delete(k)})
 vi.stubGlobal('window',{location:{origin:'https://synthetic.invalid'}})
})
it('separates Allo intent from old unknown outcome and preserves its original retry identity',async()=>{
 const path='/api/v1/orders/9/reprint'
 api.mockRejectedValueOnce(new ApiRequestError(0,'timeout'))
 await expect(manualReprint(path)).rejects.toThrow('timeout')
 const legacyStorage=[...storage.entries()][0]; const legacyKey=JSON.parse(api.mock.calls[0][1]!.body as string).idempotency_key
 scope.namespace='allo-nouilles-test';api.mockResolvedValueOnce({id:101,status:'PRINTED'} as never)
 await manualReprint(path)
 expect(JSON.parse(api.mock.calls[1][1]!.body as string).idempotency_key).not.toBe(legacyKey)
 expect(storage.get(legacyStorage[0])).toBe(legacyStorage[1])
 scope.namespace='';api.mockResolvedValueOnce({id:102,status:'PRINTED'} as never)
 await manualReprint(path)
 expect(JSON.parse(api.mock.calls[2][1]!.body as string).idempotency_key).toBe(legacyKey)
})
