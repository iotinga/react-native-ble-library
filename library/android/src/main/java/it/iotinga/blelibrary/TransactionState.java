package it.iotinga.blelibrary;

public enum TransactionState {
  PENDING,
  EXECUTING,
  CANCELED,
  SUCCEEDED,
  FAILED;

  public boolean isTerminated() {
    return this == FAILED || this == SUCCEEDED || this == CANCELED;
  }
}
