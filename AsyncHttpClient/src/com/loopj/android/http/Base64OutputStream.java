/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.loopj.android.http;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Base64输出流
 * 
 * @author lijian
 * @date 2017-7-29 下午6:59:58
 */
public class Base64OutputStream extends FilterOutputStream {
    private final Base64.Coder coder;
    private final int flags;

    private byte[] buffer = null;
    private int bpos = 0;

    private static byte[] EMPTY = new byte[0];

    /**
     * 对写入流的数据执行Base64编码，将编码数据写入另一个OutputStream。
     *
     * @param out   OutputStream将编码数据写入
     * @param flags 用于控制编码器的位标志; 请参阅{@link Base64}中的常量
     */
    public Base64OutputStream(OutputStream out, int flags) {
        this(out, flags, true);
    }

    /**
     * 对写入流的数据执行Base64编码或解码，将编码/解码的数据写入另一个OutputStream。
     *
     * @param out    OutputStream将编码数据写入
     * @param flags  用于控制编码器的位标志; 请参阅{@link Base64}中的常量
     * @param encode true：编码, false：解码
     */
    public Base64OutputStream(OutputStream out, int flags, boolean encode) {
        super(out);
        this.flags = flags;
        if (encode) {
            coder = new Base64.Encoder(flags, null);
        } else {
            coder = new Base64.Decoder(flags, null);
        }
    }

    @Override
    public void write(int b) throws IOException {
    	// 为了避免为单个字节频繁调用编码器/解码器例程，
    	// 我们缓冲在内部字节数组中写入（int）的调用，
    	// 以将它们转换为正常大小的数组的写入。

        if (buffer == null) {
            buffer = new byte[1024];
        }
        if (bpos >= buffer.length) {
            // 内部缓冲区满; 把它写出来。
            internalWrite(buffer, 0, bpos, false);
            bpos = 0;
        }
        buffer[bpos++] = (byte) b;
    }

    /**
     * 在write(byte[], int, int)或close()之前，需要将缓冲区中的数据Flush到调用者write(int)
     */
    private void flushBuffer() throws IOException {
        if (bpos > 0) {
            internalWrite(buffer, 0, bpos, false);
            bpos = 0;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len <= 0) return;
        flushBuffer();
        internalWrite(b, off, len, false);
    }

    @Override
    public void close() throws IOException {
        IOException thrown = null;
        try {
            flushBuffer();
            internalWrite(EMPTY, 0, 0, true);
        } catch (IOException e) {
            thrown = e;
        }

        try {
            if ((flags & Base64.NO_CLOSE) == 0) {
                out.close();
            } else {
                out.flush();
            }
        } catch (IOException e) {
            if (thrown != null) {
                thrown = e;
            }
        }

        if (thrown != null) {
            throw thrown;
        }
    }

    /**
     * 将给定的字节写入编码器/解码器。
     *
     * @param finish true if this is the last batch of input, to cause encoder/decoder state to be
     *               finalized.
     */
    private void internalWrite(byte[] b, int off, int len, boolean finish) throws IOException {
        coder.output = embiggen(coder.output, coder.maxOutputSize(len));
        if (!coder.process(b, off, len, finish)) {
            throw new Base64DataException("bad base-64");
        }
        out.write(coder.output, 0, coder.op);
    }

    /**
     * 如果b.length至少为len，则返回b。 否则返回一个长度为len的新字节数组。
     */
    private byte[] embiggen(byte[] b, int len) {
        if (b == null || b.length < len) {
            return new byte[len];
        } else {
            return b;
        }
    }
}