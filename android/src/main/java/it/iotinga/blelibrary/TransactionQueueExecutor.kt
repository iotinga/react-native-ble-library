package it.iotinga.blelibrary

import android.util.Log
import java.util.LinkedList
import java.util.Queue

class TransactionQueueExecutor : TransactionExecutor {
    private val queue: Queue<Transaction> = LinkedList<Transaction>()

    override fun add(transaction: Transaction) {
        Log.i(Constants.LOG_TAG, "queueing transaction " + transaction.id())

        queue.add(transaction)

        process()
    }

  override val executing: Transaction?
    get() {
        val top = queue.peek()
        return if (top == null || top.state() != TransactionState.EXECUTING) {
          null
        } else {
          top
        }
    }

    override fun process() {
        // while the queue is not empty and there are no executing tasks
        while (!queue.isEmpty() && queue.element().state() != TransactionState.EXECUTING) {
            val top = queue.element()
            if (top.state().isTerminated) {
                Log.i(
                    Constants.LOG_TAG,
                    "transaction " + top.id() + " completed with state " + top.state().name
                )
                queue.remove()
            } else if (top.state() == TransactionState.PENDING) {
                Log.i(Constants.LOG_TAG, "start transaction " + top.id())
                try {
                    top.start()
                } catch (e: Exception) {
                    Log.w(
                        Constants.LOG_TAG,
                        "unhandled exception while starting transaction: " + e.message
                    )
                    top.fail(BleError.ERROR_GENERIC, "unhandled exception: " + e.message)
                }
            }
        }
    }

    override fun cancel(id: String) {
        for (t in queue) {
            if (t.id() == id) {
                Log.i(Constants.LOG_TAG, "canceling transaction with id $id")

                t.cancel()
            }
        }
    }

    override fun flush(error: BleError, message: String) {
        Log.i(Constants.LOG_TAG, "flushing pending transaction queue")

        for (transaction in queue) {
            transaction.fail(error, message)
        }

        queue.clear()
    }
}
