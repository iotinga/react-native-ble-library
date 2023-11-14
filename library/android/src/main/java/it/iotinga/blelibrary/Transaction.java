package it.iotinga.blelibrary;

import androidx.annotation.Nullable;

public interface Transaction {
  String id();

  TransactionState state();

  void start();

  void cancel();

  void succeed(@Nullable Object result);

  void fail(BleError code, String error);
}
