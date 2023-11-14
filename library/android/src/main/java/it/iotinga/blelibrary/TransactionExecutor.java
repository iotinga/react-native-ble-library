package it.iotinga.blelibrary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface TransactionExecutor {
  void add(@NonNull Transaction transaction);

  @Nullable Transaction getExecuting();

  void process();

  void cancel(String id);

  void flush(BleError error, String message);
}
