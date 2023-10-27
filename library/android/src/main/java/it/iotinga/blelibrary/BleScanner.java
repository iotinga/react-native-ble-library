package it.iotinga.blelibrary;

import java.util.List;

public interface BleScanner {
  void start(List<String> filter) throws BleException;
  void stop() throws BleException;
}
