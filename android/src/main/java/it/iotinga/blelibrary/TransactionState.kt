package it.iotinga.blelibrary

enum class TransactionState {
    PENDING,
    EXECUTING,
    CANCELED,
    SUCCEEDED,
    FAILED;

    val isTerminated: Boolean
        get() = this == TransactionState.FAILED || this == TransactionState.SUCCEEDED || this == TransactionState.CANCELED
}
