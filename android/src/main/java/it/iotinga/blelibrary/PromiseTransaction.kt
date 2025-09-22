package it.iotinga.blelibrary

import android.util.Log
import expo.modules.kotlin.Promise

open class PromiseTransaction internal constructor(
    private val id: String,
    private val promise: Promise
) : Transaction {
    private var state = TransactionState.PENDING

    override fun id(): String {
        return id
    }

    override fun state(): TransactionState {
        return state
    }

    override fun start() {
        Log.i(Constants.LOG_TAG, "starting transaction $id")

        state = TransactionState.EXECUTING
    }

    override fun cancel() {
        if (!state.isTerminated) {
            Log.i(Constants.LOG_TAG, "canceled transaction $id")

            promise.reject(
                BleError.ERROR_OPERATION_CANCELED.name,
                "the transaction was canceled",
                null
            )
            state = TransactionState.CANCELED
        }
    }

    override fun succeed(result: Any?) {
        if (!state.isTerminated) {
            Log.i(Constants.LOG_TAG, "transaction $id succeeded")

            promise.resolve(result)
            state = TransactionState.SUCCEEDED
        }
    }

    override fun fail(code: BleError, error: String) {
        if (!state.isTerminated) {
            Log.i(
                Constants.LOG_TAG,
              "transaction $id failed (code: $code, error: $error)"
            )

            promise.reject(code.name, error, null)
            state = TransactionState.FAILED
        }
    }
}
