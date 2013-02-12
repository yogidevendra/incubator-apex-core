/*
 *  Copyright (c) 2012 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.malhartech.bufferserver.util;

import com.google.protobuf.CodedOutputStream;
import com.malhartech.bufferserver.Buffer.Message;
import java.io.IOException;

/**
 * Wrapper for a {@code byte[]}, which provides read-only access and can "reveal" a partial slice of the underlying array.<p>
 *
 *
 * <b>Note:</b> Multibyte accessors all use big-endian order.
 */
public final class SerializedData
{
  /**
   * the byte buffer where various messages including this one are stored.
   */
  public byte[] bytes;
  /**
   * the offset at which the current messages's length followed by the actual message is stored.
   */
  public int offset;
  /**
   * the offset at which the actual data begins. Between offset and dataOffset, the length of the data is stored.
   */
  public int dataOffset;
  /**
   * size is the total size of the slice of the byte array which stores the length and the message.
   */
  public int size;

  public static SerializedData getInstanceFrom(Message d) throws IOException
  {
    SerializedData sd = new SerializedData();
    int size = d.getSerializedSize();
    sd.bytes = new byte[5 + size];
    sd.offset = 0;
    sd.dataOffset = Codec.writeRawVarint32(size, sd.bytes, 0);
    sd.size = sd.dataOffset + d.getSerializedSize();
    d.writeTo(CodedOutputStream.newInstance(sd.bytes, sd.dataOffset, size));
    return sd;
  }

  public boolean isEquivalent(SerializedData sd)
  {
    if (sd != null) {
      if (sd.size == size) {
        for (int i = size; i-- > 0;) {
          if (sd.bytes[sd.offset + i] != bytes[offset + i]) {
            return false;
          }
        }

        return true;
      }
    }

    return false;
  }
  /**
   *
   * @return String
   */
  @Override
  public String toString()
  {
    return "bytes = " + bytes + " offset = " + offset + " size = " + size;
  }
}