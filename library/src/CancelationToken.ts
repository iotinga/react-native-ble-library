import type { Subscription } from './types'

export class CancelationToken {
  private onCancel = new Set<() => void>()
  private isCanceled = false

  public addListener(callback: () => void): Subscription {
    this.onCancel.add(callback)

    return {
      unsubscribe: () => {
        this.onCancel.delete(callback)
      },
    }
  }

  public get canceled() {
    return this.isCanceled
  }

  public cancel() {
    if (!this.isCanceled) {
      for (const callback of this.onCancel) {
        callback()
      }
    }
    this.isCanceled = true
  }
}
