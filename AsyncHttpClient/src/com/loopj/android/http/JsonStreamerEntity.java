/*
    Android Asynchronous Http Client
    Copyright (c) 2011 James Smith <james@loopj.com>
    https://loopj.com

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.loopj.android.http;

import android.text.TextUtils;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicHeader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * HTTP实体使用流上传JSON数据。 这具有非常低的内存占用; 适合使用base64编码上传大文件。
 */
public class JsonStreamerEntity implements HttpEntity {

    private static final String LOG_TAG = "JsonStreamerEntity";

    /** 不支持的操作异常 */
    private static final UnsupportedOperationException ERR_UNSUPPORTED =
            new UnsupportedOperationException("Unsupported operation in this implementation.");

    /** I/O流中使用的字节数组缓冲区的大小。 */
    private static final int BUFFER_SIZE = 4096;

    /** 用于从输入流读取的缓冲区  */
    private final byte[] buffer = new byte[BUFFER_SIZE];

    private static final byte[] JSON_TRUE = "true".getBytes();
    private static final byte[] JSON_FALSE = "false".getBytes();
    private static final byte[] JSON_NULL = "null".getBytes();
    private static final byte[] STREAM_NAME = escape("name");
    private static final byte[] STREAM_TYPE = escape("type");
    private static final byte[] STREAM_CONTENTS = escape("contents");

    private static final Header HEADER_JSON_CONTENT =
            new BasicHeader(
                    AsyncHttpClient.HEADER_CONTENT_TYPE,
                    RequestParams.APPLICATION_JSON);

    private static final Header HEADER_GZIP_ENCODING =
            new BasicHeader(
                    AsyncHttpClient.HEADER_CONTENT_ENCODING,
                    AsyncHttpClient.ENCODING_GZIP);

    /** 要上传的JSON数据和关联的元数据 */ 
    private final Map<String, Object> jsonParams = new HashMap<String, Object>();

    /** 是否在上传时使用gzip压缩 */
    private final Header contentEncoding;

    /** 已过场 */
    private final byte[] elapsedField;

    /** 进度处理器 */
    private final ResponseHandlerInterface progressHandler;

    public JsonStreamerEntity(ResponseHandlerInterface progressHandler, boolean useGZipCompression, String elapsedField) {
        this.progressHandler = progressHandler;
        this.contentEncoding = useGZipCompression ? HEADER_GZIP_ENCODING : null;
        this.elapsedField = TextUtils.isEmpty(elapsedField)
                ? null
                : escape(elapsedField);
    }

    /**
     * 将由给定键标识的内容参数添加到请求中。
     *
     * @param key   实体名称
     * @param value 实体的值（Scalar，FileWrapper，StreamWrapper）
     */
    public void addPart(String key, Object value) {
        jsonParams.put(key, value);
    }

    /**
     * 是可重复的
     */
    @Override
    public boolean isRepeatable() {
        return false;
    }

    /**
     * 是分块
     */
    @Override
    public boolean isChunked() {
        return false;
    }

    /**
     * 是流式传输
     */
    @Override
    public boolean isStreaming() {
        return false;
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public Header getContentEncoding() {
        return contentEncoding;
    }

    @Override
    public Header getContentType() {
        return HEADER_JSON_CONTENT;
    }

    /**
     * 消费内容
     */
    @Override
    public void consumeContent() throws IOException, UnsupportedOperationException {
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        throw ERR_UNSUPPORTED;
    }

    @Override
    public void writeTo(final OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalStateException("Output stream cannot be null.");
        }

        // 记录上传开始的时间。
        long now = System.currentTimeMillis();

        // 发送流时使用GZIP压缩，否则只需使用缓冲输出流即可加快速度。
        OutputStream os = contentEncoding != null
                ? new GZIPOutputStream(out, BUFFER_SIZE)
                : out;

        // 始终发送一个JSON对象。
        os.write('{');

        // Keys used by the HashMaps.
        Set<String> keys = jsonParams.keySet();

        int keysCount = keys.size();
        if (0 < keysCount) {
            int keysProcessed = 0;
            boolean isFileWrapper;

            // 转过所有的键并处理每个值。
            for (String key : keys) {
                // 表示该key已被处理。
                keysProcessed++;

                try {
                    // 评估值（不能为null）。
                    Object value = jsonParams.get(key);

                    // 编写JSON对象key。
                    os.write(escape(key));
                    os.write(':');

                    // 如果值为null，则提前处理。
                    if (value == null) {
                        os.write(JSON_NULL);
                    } else {
                        // 检查这是否是FileWrapper。
                        isFileWrapper = value instanceof RequestParams.FileWrapper;

                        // 如果一个文件应该被上传。
                        if (isFileWrapper || value instanceof RequestParams.StreamWrapper) {
                            // 所有上传都作为包含文件详细信息的对象发送。
                            os.write('{');

                            // 确定如何处理此条目。
                            if (isFileWrapper) {
                                writeToFromFile(os, (RequestParams.FileWrapper) value);
                            } else {
                                writeToFromStream(os, (RequestParams.StreamWrapper) value);
                            }

                            // 结束文件的对象并准备下一个。
                            os.write('}');
                        } else if (value instanceof JsonValueInterface) {
                            os.write(((JsonValueInterface) value).getEscapedJsonValue());
                        } else if (value instanceof org.json.JSONObject) {
                            os.write(value.toString().getBytes());
                        } else if (value instanceof org.json.JSONArray) {
                            os.write(value.toString().getBytes());
                        } else if (value instanceof Boolean) {
                            os.write((Boolean) value ? JSON_TRUE : JSON_FALSE);
                        } else if (value instanceof Long) {
                            os.write((((Number) value).longValue() + "").getBytes());
                        } else if (value instanceof Double) {
                            os.write((((Number) value).doubleValue() + "").getBytes());
                        } else if (value instanceof Float) {
                            os.write((((Number) value).floatValue() + "").getBytes());
                        } else if (value instanceof Integer) {
                            os.write((((Number) value).intValue() + "").getBytes());
                        } else {
                            os.write(escape(value.toString()));
                        }
                    }
                } finally {
                    // 用逗号分隔每个K:V，除了最后一个。
                    if (elapsedField != null || keysProcessed < keysCount) {
                        os.write(',');
                    }
                }
            }

            // 计算上传内容所需的毫秒数。
            long elapsedTime = System.currentTimeMillis() - now;

            // 包括上传所有时间所花费的时间。这可能对某人有用，但它为我们服务，因为几乎总是作为最后一个发送的角色。
            if (elapsedField != null) {
                os.write(elapsedField);
                os.write(':');
                os.write((elapsedTime + "").getBytes());
            }

            AsyncHttpClient.log.i(LOG_TAG, "Uploaded JSON in " + Math.floor(elapsedTime / 1000) + " seconds");
        }

        // 关闭JSON对象。
        os.write('}');

        // 将内容刷新流。
        os.flush();
        AsyncHttpClient.silentCloseOutputStream(os);
    }

    private void writeToFromStream(OutputStream os, RequestParams.StreamWrapper entry)
            throws IOException {

        // 发送元数据。
        writeMetaData(os, entry.name, entry.contentType);

        int bytesRead;

        // 上传文件的内容在Base64。
        Base64OutputStream bos =
                new Base64OutputStream(os, Base64.NO_CLOSE | Base64.NO_WRAP);

        // 从输入流中读取数据，直到不再读取数据为止。
        while ((bytesRead = entry.inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
        }

        // 关闭Base64 output stream.
        AsyncHttpClient.silentCloseOutputStream(bos);

        // 结束元数据。
        endMetaData(os);

        // Close input stream.
        if (entry.autoClose) {
            // Safely close the input stream.
            AsyncHttpClient.silentCloseInputStream(entry.inputStream);
        }
    }

    private void writeToFromFile(OutputStream os, RequestParams.FileWrapper wrapper)
            throws IOException {

        // Send the meta data.
        writeMetaData(os, wrapper.file.getName(), wrapper.contentType);

        int bytesRead;
        long bytesWritten = 0, totalSize = wrapper.file.length();

        // Open the file for reading.
        FileInputStream in = new FileInputStream(wrapper.file);

        // Upload the file's contents in Base64.
        Base64OutputStream bos =
                new Base64OutputStream(os, Base64.NO_CLOSE | Base64.NO_WRAP);

        // Read from file until no more data's left to read.
        while ((bytesRead = in.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
            bytesWritten += bytesRead;
            progressHandler.sendProgressMessage(bytesWritten, totalSize);
        }

        // Close the Base64 output stream.
        AsyncHttpClient.silentCloseOutputStream(bos);

        // End the meta data.
        endMetaData(os);

        // Safely close the input stream.
        AsyncHttpClient.silentCloseInputStream(in);
    }

    private void writeMetaData(OutputStream os, String name, String contentType) throws IOException {
        // Send the streams's name.
        os.write(STREAM_NAME);
        os.write(':');
        os.write(escape(name));
        os.write(',');

        // Send the streams's content type.
        os.write(STREAM_TYPE);
        os.write(':');
        os.write(escape(contentType));
        os.write(',');

        // Prepare the file content's key.
        os.write(STREAM_CONTENTS);
        os.write(':');
        os.write('"');
    }

    private void endMetaData(OutputStream os) throws IOException {
        os.write('"');
    }

    // 感谢 Simple-JSON: https://goo.gl/XoW8RF
    // 改变了一点，以适应我们在这个class的需要。
    static byte[] escape(String string) {
        // 如果它为null，提前返回。
        if (string == null) {
            return JSON_NULL;
        }

        // 创建一个字符串构建器来生成转义的字符串。
        StringBuilder sb = new StringBuilder(128);

        // Surround with quotations.
        sb.append('"');

        int length = string.length(), pos = -1;
        while (++pos < length) {
            char ch = string.charAt(pos);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    // 参考: https://www.unicode.org/versions/Unicode5.1.0/
                    if ((ch <= '\u001F') || (ch >= '\u007F' && ch <= '\u009F') || (ch >= '\u2000' && ch <= '\u20FF')) {
                        String intString = Integer.toHexString(ch);
                        sb.append("\\u");
                        // 不足4位，前面补零
                        int intLength = 4 - intString.length();
                        for (int zero = 0; zero < intLength; zero++) {
                            sb.append('0');
                        }
                        sb.append(intString.toUpperCase(Locale.US));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }

        // Surround with quotations.
        sb.append('"');

        return sb.toString().getBytes();
    }
}
