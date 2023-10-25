package it.iotinga.blelibrary;

public interface ChunkedReadComposer {
  boolean hasMoreChunks();
  int getReceivedBytes();
  int getRemainingBytes();
  int getTotalBytes();
  byte[] getBytes();
  void putChunk(byte[] bytes);
}
