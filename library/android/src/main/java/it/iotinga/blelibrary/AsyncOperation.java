package it.iotinga.blelibrary;

public interface AsyncOperation {
  boolean isPending();
  void complete(Object result);
  void complete();
  void fail(Exception exception);
}
