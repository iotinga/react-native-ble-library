import { useBleManager } from './useBleManager'

export function useBlePermissions(): [boolean, () => Promise<boolean>] {
  const [state, manager] = useBleManager()

  const request = () => {
    return manager.askPermissions()
  }

  return [state.permission.granted === true, request]
}
