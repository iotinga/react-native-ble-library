package it.iotinga.blelibrary;

import com.facebook.react.bridge.Promise;

public class PromiseAsyncOperation implements AsyncOperation {
  private final Promise promise;
  private boolean isPending = true;

  PromiseAsyncOperation(Promise promise) {
    this.promise = promise;
  }

  @Override
  public boolean isPending() {
    return isPending;
  }

  @Override
  public void complete(Object result) {
    if (isPending) {
      isPending = false;
      promise.resolve(result);
    }
  }

  @Override
  public void complete() {
    complete(null);
  }

  @Override
  public void fail(Exception exception) {
    if (isPending) {
      isPending = false;
      if (exception instanceof BleException) {
        BleException bleException = (BleException) exception;
        promise.reject(bleException.getCode(), exception.getMessage(), bleException.getDetails());
      } else {
        promise.reject(BleException.DEFAULT_ERROR_CODE, exception.getMessage());
      }
    }
  }
}

