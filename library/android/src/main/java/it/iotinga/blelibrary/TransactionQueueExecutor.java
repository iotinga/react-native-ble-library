package it.iotinga.blelibrary;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

public class TransactionQueueExecutor implements TransactionExecutor {

  private final Queue<Transaction> queue = new LinkedList<>();

  @Override
  public void add(@NonNull Transaction transaction) {
    Log.i(Constants.LOG_TAG, "queueing transaction " + transaction.id());

    queue.add(transaction);

    process();
  }

  @Override
  public Transaction getExecuting() {
    Transaction top = queue.peek();
    if (top == null || top.state() != TransactionState.EXECUTING) {
      return null;
    } else {
      return top;
    }
  }

  @Override
  public void process() {
    // while the queue is not empty and there are no executing tasks
    while (!queue.isEmpty() && queue.element().state() != TransactionState.EXECUTING) {
      Transaction top = queue.element();
      if (top.state().isTerminated()) {
        Log.i(Constants.LOG_TAG, "transaction " + top.id() + " completed with state " + top.state().name());
        queue.remove();
      } else if (top.state() == TransactionState.PENDING) {
        Log.i(Constants.LOG_TAG, "start transaction " + top.id());
        try {
          top.start();
        } catch (Exception e) {
          Log.w(Constants.LOG_TAG, "unhandled exception while starting transaction: " + e.getMessage());
          top.fail(BleError.ERROR_GENERIC, "unhandled exception: " + e.getMessage());
        }
      }
    }
  }

  @Override
  public void cancel(String id) {
    for (Transaction t : queue) {
      if (Objects.equals(t.id(), id)) {
        Log.i(Constants.LOG_TAG, "canceling transaction with id " + id);

        t.cancel();
      }
    }
  }

  @Override
  public void flush(BleError error, String message) {
    Log.i(Constants.LOG_TAG, "flushing pending transaction queue");

    for (Transaction transaction : queue) {
      transaction.fail(error, message);
    }

    queue.clear();
  }
}
