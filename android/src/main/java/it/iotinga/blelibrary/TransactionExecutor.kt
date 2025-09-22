package it.iotinga.blelibrary

interface TransactionExecutor {
    fun add(transaction: Transaction)

    val executing: Transaction?

    fun process()

    fun cancel(id: String)

    fun flush(error: BleError, message: String)
}
