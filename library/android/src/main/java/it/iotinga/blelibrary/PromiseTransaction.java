package it.iotinga.blelibrary;

import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;

public class PromiseTransaction implements Transaction {
  private static final String TAG = "PromiseTransaction";
  private final String id;
  private final Promise promise;
  private TransactionState state = TransactionState.PENDING;

  PromiseTransaction(String id, Promise promise) {
    this.id = id;
    this.promise = promise;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public TransactionState state() {
    return state;
  }

  @Override
  public void start() {
    Log.i(TAG, "starting transaction " + id);

    state = TransactionState.EXECUTING;
  }

  @Override
  public void cancel() {
    if (!state.isTerminated()) {
      Log.i(TAG, "canceled transaction " + id);

      promise.reject(BleError.ERROR_OPERATION_CANCELED.name(), "the transaction was canceled");
      state = TransactionState.CANCELED;
    }
  }

  @Override
  public void succeed(@Nullable Object result) {
    if (!state.isTerminated()) {
      Log.i(TAG, "transaction " + id + " succeeded");

      promise.resolve(result);
      state = TransactionState.SUCCEEDED;
    }
  }

  @Override
  public void fail(BleError code, String error) {
    if (!state.isTerminated()) {
      Log.i(TAG, "transaction " + id + " failed (code: " + code + ", error: " + error + ")");

      promise.reject(code.name(), error);
      state = TransactionState.FAILED;
    }
  }
}
