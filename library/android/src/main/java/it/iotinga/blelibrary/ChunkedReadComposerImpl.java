package it.iotinga.blelibrary;

public class ChunkedReadComposerImpl implements ChunkedReadComposer {
  private int receivedBytes = 0;
  private final byte[] data;

  ChunkedReadComposerImpl(int totalSize) {
    data = new byte[totalSize];
  }

  @Override
  public boolean hasMoreChunks() {
    return getRemainingBytes() > 0;
  }

  @Override
  public int getReceivedBytes() {
    return receivedBytes;
  }

  @Override
  public int getRemainingBytes() {
    return getTotalBytes() - getReceivedBytes();
  }

  @Override
  public int getTotalBytes() {
    return data.length;
  }

  @Override
  public byte[] getBytes() {
    return data;
  }

  @Override
  public void putChunk(byte[] bytes) {
    for (byte b : bytes) {
      data[receivedBytes++] = b;
    }
  }
}
