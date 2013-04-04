/*
 *  Copyright (c) 2012-2013 Malhar, Inc.
 *  All Rights Reserved.
 */
package com.malhartech.bufferserver.client;

import com.malhartech.bufferserver.util.Codec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import malhar.netlet.Client;
import malhar.netlet.DefaultEventLoop;
import malhar.netlet.EventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chetan Narsude <chetan@malhar-inc.com>
 */
public abstract class AbstractClient extends Client
{
  protected byte[] buffer;
  protected ByteBuffer byteBuffer;
  protected int size, writeOffset, readOffset;
  private EventLoop eventloop;
  private InetSocketAddress address;

  public AbstractClient()
  {
    super();
    buffer = new byte[32 * 1024];
    byteBuffer = ByteBuffer.wrap(buffer);
  }

  public AbstractClient(byte[] readbuffer, int position)
  {
    super();
    buffer = readbuffer;
    byteBuffer = ByteBuffer.wrap(readbuffer);
    byteBuffer.position(position);
    writeOffset = position;
    readOffset = position;
  }

  @Override
  public ByteBuffer buffer()
  {
    return byteBuffer;
  }

  // -ve number is no var int
  public int readVarInt()
  {
    if (readOffset < writeOffset) {
      int offset = readOffset;

      byte tmp = buffer[readOffset++];
      if (tmp >= 0) {
        return tmp;
      }
      else if (readOffset < writeOffset) {
        int integer = tmp & 0x7f;
        tmp = buffer[readOffset++];
        if (tmp >= 0) {
          return integer | tmp << 7;
        }
        else if (readOffset < writeOffset) {
          integer |= (tmp & 0x7f) << 7;
          tmp = buffer[readOffset++];

          if (tmp >= 0) {
            return integer | tmp << 14;
          }
          else if (readOffset < writeOffset) {
            integer |= (tmp & 0x7f) << 14;
            tmp = buffer[readOffset++];
            if (tmp >= 0) {
              return integer | tmp << 21;
            }
            else if (readOffset < writeOffset) {
              integer |= (tmp & 0x7f) << 21;
              tmp = buffer[readOffset++];
              if (tmp >= 0) {
                return integer | tmp << 28;
              }
              else {
                throw new NumberFormatException("Invalid varint at location " + offset + " => "
                        + Arrays.toString(Arrays.copyOfRange(buffer, offset, readOffset)));
              }
            }
          }
        }
      }

      readOffset = offset;
    }
    return -1;
  }

  /**
   * Upon reading the data from the socket into the byteBuffer, this method is called.
   *
   * @param len - length of the data in number of bytes read into the byteBuffer during the most recent read.
   */
  @Override
  public void read(int len)
  {
    writeOffset += len;
    do {
      while (size == 0) {
        size = readVarInt();
        if (size == -1) {
          if (writeOffset == buffer.length) {
            if (readOffset > writeOffset - 5) {
              logger.info("hit the boundary while reading varint!");
              /*
               * we may be reading partial varint, adjust the buffers so that we have enough space to read the full data.
               */
              System.arraycopy(buffer, readOffset, buffer, 0, writeOffset - readOffset);
              writeOffset -= readOffset;
              byteBuffer.position(writeOffset);
              readOffset = 0;
            }
          }
          size = 0;
          return;
        }
      }

      if (writeOffset - readOffset >= size) {
        onMessage(buffer, readOffset, size);
        readOffset += size;
        size = 0;
      }
      else if (writeOffset == buffer.length) {
        if (size > buffer.length) {
          int newsize = buffer.length;
          while (newsize < size) {
            newsize <<= 1;
          }
          logger.info("resizing buffer to size {} from size {}", newsize, buffer.length);
          byte[] newArray = new byte[newsize];
          System.arraycopy(buffer, readOffset, newArray, 0, writeOffset - readOffset);
          writeOffset -= readOffset;
          readOffset = 0;
          byteBuffer = ByteBuffer.wrap(newArray);
          byteBuffer.position(writeOffset);
        }
        else {
          System.arraycopy(buffer, readOffset, buffer, 0, writeOffset - readOffset);
          writeOffset -= readOffset;
          readOffset = 0;
          byteBuffer.clear();
          byteBuffer.position(writeOffset);
        }
      }
      else {       /* need to read more */
        return;
      }
    }
    while (true);
  }

  public void write(byte[] message)
  {
    write(message, 0, message.length);
  }

  private int intOffset;
  private static final int INT_ARRAY_SIZE = 4096 - 5;
  private byte[] intBuffer = new byte[INT_ARRAY_SIZE + 5];

  public void write(byte[] message, int offset, int size)
  {
    if (intOffset > INT_ARRAY_SIZE) {
      intBuffer = new byte[INT_ARRAY_SIZE + 5];
      intOffset = 0;
    }

    int newOffset = Codec.writeRawVarint32(size, intBuffer, intOffset);
    try {
      send(intBuffer, intOffset, newOffset - intOffset);
      intOffset = newOffset;
      send(message, offset, size);
    }
    catch (InterruptedException ie) {
      throw new RuntimeException(ie);
    }
  }

  public void setup(InetSocketAddress address, EventLoop eventloop)
  {
    this.address = address;
    this.eventloop = eventloop;
  }

  public void teardown()
  {
  }

  public void activate()
  {
    eventloop.connect(address.isUnresolved()? new InetSocketAddress(address.getHostName(), address.getPort()): address, this);
  }

  public void deactivate()
  {
    eventloop.disconnect(this);
  }

  @Override
  public void handleException(Exception cce, DefaultEventLoop el)
  {
    if (cce instanceof IOException) {
      el.disconnect(this);
    }
    else {
      throw new RuntimeException(cce);
    }
  }

  public abstract void onMessage(byte[] buffer, int offset, int size);

  private static final Logger logger = LoggerFactory.getLogger(AbstractClient.class);
}
