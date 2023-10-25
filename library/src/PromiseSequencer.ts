export class PromiseSequencer {
  private pendingPromise: Promise<any> = Promise.resolve()

  execute<T>(fn: () => Promise<T>): Promise<T> {
    return (this.pendingPromise = this.pendingPromise.then(() => fn()))
  }
}
