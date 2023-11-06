export class PromiseSequencer {
  private pendingPromise: Promise<any> = Promise.resolve()

  execute<T>(fn: () => Promise<T>, timeout?: number): Promise<T> {
    const promise = (this.pendingPromise = this.pendingPromise.catch(() => {}).then(() => fn()))

    if (timeout) {
      const timeoutPromise = new Promise<T>((_, reject) => {
        setTimeout(() => reject(new Error('timeout')), timeout)
      })
      return Promise.race([promise, timeoutPromise])
    } else {
      return promise
    }
  }
}
