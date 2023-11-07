package it.iotinga.blelibrary;

import androidx.annotation.Nullable;

public interface RNEvent {
  String name();
  @Nullable Object payload();
}
