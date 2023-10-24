package it.iotinga.blelibrary;

public interface ChunkedWriteSplitter {
  int getWrittenBytes();
  int getTotalBytes();
  int getRemainingBytes();
  boolean hasNextChunk();
  byte[] getNextChunk();
}
