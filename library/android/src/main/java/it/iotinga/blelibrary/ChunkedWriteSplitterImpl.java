package it.iotinga.blelibrary;

import java.util.Arrays;

public class ChunkedWriteSplitterImpl implements ChunkedWriteSplitter {
  private final byte[] bytes;
  private final int chunkSize;
  private int writtenBytes = 0;

  ChunkedWriteSplitterImpl(byte[] bytes, int chunkSize) {
    this.bytes = bytes;
    this.chunkSize = chunkSize;
  }

  public int getWrittenBytes() {
    return writtenBytes;
  }

  public int getTotalBytes() {
    return bytes.length;
  }

  public int getRemainingBytes() {
    return getTotalBytes() - getWrittenBytes();
  }

  public boolean hasNextChunk() {
    return getRemainingBytes() > 0;
  }

  public byte[] getNextChunk() {
    if (!hasNextChunk()) {
      return null;
    }

    int rangeEnd = Math.min(writtenBytes + chunkSize, bytes.length);
    byte[] chunk = Arrays.copyOfRange(bytes, writtenBytes, rangeEnd);
    writtenBytes += chunk.length;

    return chunk;
  }
}
