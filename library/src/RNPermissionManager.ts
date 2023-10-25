import { RESULTS, checkMultiple, requestMultiple, type Permission } from 'react-native-permissions'
import type { IPermissionManager } from './types'

export class RNPermissionManager implements IPermissionManager<Permission> {
  async askPermissions(permissions: Permission[]): Promise<boolean> {
    const statuses = await requestMultiple(permissions)
    for (const status of Object.values(statuses)) {
      if (status !== RESULTS.GRANTED && status !== RESULTS.UNAVAILABLE) {
        return false
      }
    }

    return true
  }

  askPermission(permission: Permission | Permission[]): Promise<boolean> {
    if (Array.isArray(permission)) {
      return this.askPermissions(permission)
    } else {
      return this.askPermissions([permission])
    }
  }

  async hasPermissions(permissions: Permission[]): Promise<boolean> {
    const statuses = await checkMultiple(permissions)
    for (const status of Object.values(statuses)) {
      if (status !== RESULTS.GRANTED && status !== RESULTS.UNAVAILABLE) {
        return false
      }
    }

    return true
  }

  hasPermission(permission: Permission | Permission[]): Promise<boolean> {
    if (Array.isArray(permission)) {
      return this.askPermissions(permission)
    } else {
      return this.askPermissions([permission])
    }
  }
}
