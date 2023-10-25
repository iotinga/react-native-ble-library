import { DemoBleManager } from './DemoBleManager'
import type { DemoState, IBleManager, IBleManagerFactory } from './types'

export class DemoBleManagerFactory implements IBleManagerFactory {
  constructor(private readonly demo: DemoState) {}

  create(): IBleManager {
    return new DemoBleManager(this.demo)
  }
}
