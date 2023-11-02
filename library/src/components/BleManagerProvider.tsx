import React, { useEffect, useMemo, useState } from 'react'
import { BleManagerContext } from '../contexts/BleManagerContext'
import type { IBleManager, IBleManagerFactory, ILogger } from '../types'

export function BleManagerProvider({
  children,
  factory,
  logger,
}: {
  children: React.ReactNode
  factory: IBleManagerFactory
  logger?: ILogger
}) {
  const [manager, setManager] = useState<IBleManager | null>(null)

  useEffect(() => {
    const manager = factory.create(logger)
    setManager(manager)

    return () => {
      manager.dispose()
    }
  }, [factory, logger])

  if (manager === null) {
    return null
  }

  return <BleManagerContext.Provider value={manager}>{children}</BleManagerContext.Provider>
}
