package it.iotinga.blelibrary

interface Transaction {
    fun id(): String

    fun state(): TransactionState

    fun start()

    fun cancel()

    fun succeed(result: Any?)

    fun fail(code: BleError, error: String)
}
