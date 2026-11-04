import { afterEach, expect, it } from 'vitest'
import { appI18n } from './engine'
import { userMessageForStatus } from '../services/apiClient'

afterEach(async () => { await appI18n.changeLanguage('en') })
it('localizes static API failures without exposing technical backend messages or changing a safe validation message', async () => {
  await appI18n.changeLanguage('fr-CA')
  expect(userMessageForStatus(401)).toBe('Session expirée. Veuillez vous reconnecter.')
  expect(userMessageForStatus(403, 'Store scope denied')).toBe('Vous n’avez pas accès à ce restaurant.')
  const failure = userMessageForStatus(500, 'java.lang.Exception: private diagnostic')
  expect(failure).not.toContain('private diagnostic')
  expect(failure).not.toContain('java.')
  expect(userMessageForStatus(400, 'CHOICE_COUNT_INVALID')).toBe('CHOICE_COUNT_INVALID')
})
