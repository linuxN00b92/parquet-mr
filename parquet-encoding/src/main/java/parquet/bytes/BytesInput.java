/**
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package parquet.bytes;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import parquet.Log;


/**
 * A source of bytes capable of writing itself to an output.
 * A BytesInput should be consumed right away.
 * It is not a container.
 * For example if it is referring to a stream,
 * subsequent BytesInput reads from the stream will be incorrect
 * if the previous has not been consumed.
 *
 * @author Julien Le Dem
 *
 */
abstract public class BytesInput {
  private static final Log LOG = Log.getLog(BytesInput.class);
  private static final boolean DEBUG = false;//Log.DEBUG;
  private static final EmptyBytesInput EMPTY_BYTES_INPUT = new EmptyBytesInput();

  /**
   * logically concatenate the provided inputs
   * @param inputs the inputs to concatenate
   * @return a concatenated input
   */
  public static BytesInput concat(BytesInput... inputs) {
    return new SequenceBytesIn(Arrays.asList(inputs));
  }

  /**
   * logically concatenate the provided inputs
   * @param inputs the inputs to concatenate
   * @return a concatenated input
   */
  public static BytesInput concat(List<BytesInput> inputs) {
    return new SequenceBytesIn(inputs);
  }

  /**
   * @param in
   * @param bytes number of bytes to read
   * @return a BytesInput that will read that number of bytes from the stream
   */
  public static BytesInput from(InputStream in, int bytes) {
    return new StreamBytesInput(in, bytes);
  }

  /**
   *
   * @param in
   * @return a Bytes input that will write the given bytes
   */
  public static BytesInput from(byte[] in) {
    if (DEBUG) LOG.debug("BytesInput from array of " + in.length + " bytes");
    return new ByteArrayBytesInput(in, 0 , in.length);
  }

  public static BytesInput from(byte[] in, int offset, int length) {
    if (DEBUG) LOG.debug("BytesInput from array of " + length + " bytes");
    return new ByteArrayBytesInput(in, offset, length);
  }

  /**
   * @param intValue the int to write
   * @return a BytesInput that will write 4 bytes in little endian
   */
  public static BytesInput fromInt(int intValue) {
    return new IntBytesInput(intValue);
  }

  /**
   * @param arrayOut
   * @return a BytesInput that will write the content of the buffer
   */
  public static BytesInput from(CapacityByteArrayOutputStream arrayOut) {
    return new CapacityBAOSBytesInput(arrayOut);
  }

  /**
   * @param baos
   * @return a BytesInput that will write the content of the buffer
   */
  public static BytesInput from(ByteArrayOutputStream baos) {
    return new BAOSBytesInput(baos);
  }

  /**
   * @return an empty bytes input
   */
  public static BytesInput empty() {
    return EMPTY_BYTES_INPUT;
  }

  /**
   * copies the input into a new byte array
   * @param bytesInput
   * @return
   * @throws IOException
   */
  public static BytesInput copy(BytesInput bytesInput) throws IOException {
    return from(bytesInput.toByteArray());
  }

  /**
   * writes the bytes into a stream
   * @param out
   * @throws IOException
   */
  abstract public void writeAllTo(OutputStream out) throws IOException;

  /**
   *
   * @return a new byte array materializing the contents of this input
   * @throws IOException
   */
  public byte[] toByteArray() throws IOException {
    BAOS baos = new BAOS((int)size());
    this.writeAllTo(baos);
    if (DEBUG) LOG.debug("converted " + size() + " to byteArray of " + baos.size() + " bytes");
    return baos.getBuf();
  }

  /**
   * writes length bytes to an existing buffer
   *
   * @param buffer buffer to write to
   * @param start where to start writing in the buffer
   * @param length hown many bytes to write to the buffer
   * @return the remaining bytes in the input
   */
  public abstract BytesInput writeTo(byte[] buffer, int start, int length) throws IOException;

  /**
   *
   * @return the size in bytes that would be written
   */
  abstract public long size();

  private static final class BAOS extends ByteArrayOutputStream {
    private BAOS(int size) {
      super(size);
    }

    public byte[] getBuf() {
      return this.buf;
    }
  }

  private static class StreamBytesInput extends BytesInput {
    private static final Log LOG = Log.getLog(BytesInput.StreamBytesInput.class);
    private final InputStream in;
    private final int byteCount;

    private StreamBytesInput(InputStream in, int byteCount) {
      super();
      this.in = in;
      this.byteCount = byteCount;
    }

    @Override
    public void writeAllTo(OutputStream out) throws IOException {
      if (DEBUG) LOG.debug("write All "+ byteCount + " bytes");
      // TODO: more efficient
      out.write(this.toByteArray());
    }

    public BytesInput writeTo(byte[] buffer, int start, int length) throws IOException {
      assert length <= size();
      assert length > 0;
      new DataInputStream(in).readFully(buffer, start, length);
      return from(in, (int) size() - length);
    }

    public byte[] toByteArray() throws IOException {
      if (DEBUG) LOG.debug("read all "+ byteCount + " bytes");
      byte[] buf = new byte[byteCount];
      new DataInputStream(in).readFully(buf);
      return buf;
    }


    @Override
    public long size() {
      return byteCount;
    }

  }

  private static class SequenceBytesIn extends BytesInput {
    private static final Log LOG = Log.getLog(BytesInput.SequenceBytesIn.class);

    private final List<BytesInput> inputs;
    private final long size;

    private SequenceBytesIn(List<BytesInput> inputs) {
      this.inputs = inputs;
      long total = 0;
      for (BytesInput input : inputs) {
        total += input.size();
      }
      this.size = total;
    }

    @SuppressWarnings("unused")
    @Override
    public void writeAllTo(OutputStream out) throws IOException {
      for (BytesInput input : inputs) {
        if (DEBUG) LOG.debug("write " + input.size() + " bytes to out");
        if (DEBUG && input instanceof SequenceBytesIn) LOG.debug("{");
        input.writeAllTo(out);
        if (DEBUG && input instanceof SequenceBytesIn) LOG.debug("}");
      }
    }

    public BytesInput writeTo(byte[] buffer, int start, int length) throws IOException {
      assert length <= size();
      assert length > 0;
      int writtenSoFar = 0;
      for (BytesInput input : inputs) {
        if (DEBUG) LOG.debug("write " + input.size() + " bytes to out");
        if (DEBUG && input instanceof SequenceBytesIn) LOG.debug("{");
        input.writeTo(buffer, start, (int) Math.min(length - writtenSoFar, input.size()));
        if (input.size() < length){
          writtenSoFar += input.size();
        }
        else{
          break;
        }
        if (writtenSoFar == length){
          break;
        }
        if (DEBUG && input instanceof SequenceBytesIn) LOG.debug("}");
      }
      return from(buffer, start, length);
    }

    @Override
    public long size() {
      return size;
    }

  }

  private static class IntBytesInput extends BytesInput {

    private final int intValue;

    public IntBytesInput(int intValue) {
      this.intValue = intValue;
    }

    @Override
    public void writeAllTo(OutputStream out) throws IOException {
      BytesUtils.writeIntLittleEndian(out, intValue);
    }

    public BytesInput writeTo(byte[] buffer, int start, int length) throws IOException {
      assert length <= size();
      assert length > 0;
      BytesUtils.writeIntLittleEndian(buffer, start, intValue);
      return from(buffer, start, length);
    }

    @Override
    public long size() {
      return 4;
    }

  }

  private static class EmptyBytesInput extends BytesInput {

    @Override
    public void writeAllTo(OutputStream out) throws IOException {
    }

    @Override
    public BytesInput writeTo(byte[] buffer, int start, int length) throws IOException {
      // TODO - Not entirely sure this is the right thing to do here.
      throw new UnsupportedOperationException();
    }

    @Override
    public long size() {
      return 0;
    }

  }

  private static class CapacityBAOSBytesInput extends BytesInput {

    private final CapacityByteArrayOutputStream arrayOut;

    private CapacityBAOSBytesInput(CapacityByteArrayOutputStream arrayOut) {
      this.arrayOut = arrayOut;
    }

    @Override
    public void writeAllTo(OutputStream out) throws IOException {
      arrayOut.writeTo(out);
    }

    public BytesInput writeTo(byte[] buffer, int start, int length) throws IOException {
      assert length <= size();
      assert length > 0;
      // TODO - is there a more efficient way to do this? Could not find a way to get the byte[] wrapped in an output
      // stream, not sure if there is another method for getting the data out, similar issue in BAOSBytesInput
      ByteArrayOutputStream BAOS = new ByteArrayOutputStream(length);
      arrayOut.writeTo(BAOS);
      return from(BAOS).writeTo(buffer, start, length);
    }

    @Override
    public long size() {
      return arrayOut.size();
    }

  }

  private static class BAOSBytesInput extends BytesInput {

    private final ByteArrayOutputStream arrayOut;

    private BAOSBytesInput(ByteArrayOutputStream arrayOut) {
      this.arrayOut = arrayOut;
    }

    @Override
    public void writeAllTo(OutputStream out) throws IOException {
      arrayOut.writeTo(out);
    }

    public BytesInput writeTo(byte[] buffer, int start, int length) {
      assert length <= size();
      assert length > 0;
      // TODO - is there a more efficient way to do this? Could not find a way to get the byte[] wrapped in an output
      // stream, not sure if there is another method for getting the data out, same issue in CapacityBAOSByteInput
      byte[] tempBuf = arrayOut.toByteArray();
      System.arraycopy(tempBuf, 0, buffer, start, length);
      return from(buffer);
    }

    @Override
    public long size() {
      return arrayOut.size();
    }

  }

  private static class ByteArrayBytesInput extends BytesInput {

    private final byte[] in;
    private final int offset;
    private final int length;

    private ByteArrayBytesInput(byte[] in, int offset, int length) {
      this.in = in;
      this.offset = offset;
      this.length = length;
    }

    @Override
    public void writeAllTo(OutputStream out) throws IOException {
      out.write(in, offset, length);
    }

    @Override
    public BytesInput writeTo(byte[] buffer, int start, int length) {
      assert length <= size();
      assert length > 0;
      System.arraycopy(in, offset, buffer, start, length);
      return from(buffer, start, length);
    }

    @Override
    public long size() {
      return length;
    }

  }

}
