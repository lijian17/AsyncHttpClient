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

import org.apache.http.Header;

import java.io.UnsupportedEncodingException;

/**
 * 用于拦截和处理使用{@link AsyncHttpClient}的请求的响应。 
 * {@link #onSuccess(int, org.apache.http.Header[], byte[])}方法被设计为用您自己的响应处理代码进行匿名覆盖。
 * <p>&nbsp;</p>
 * 另外，您可以根据需要覆盖{@link #onFailure(int, org.apache.http.Header[], byte[], Throwable)}, {@link #onStart()}和
 * {@link #onFinish()}方法。
 * 
 * <p>&nbsp;</p> For example: <p>&nbsp;</p>
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * client.get("https://www.baidu.com", new TextHttpResponseHandler() {
 *     &#064;Override
 *     public void onStart() {
 *         // Initiated the request
 *     }
 *
 *     &#064;Override
 *     public void onSuccess(String responseBody) {
 *         // Successfully got a response
 *     }
 *
 *     &#064;Override
 *     public void onFailure(String responseBody, Throwable e) {
 *         // Response failed :(
 *     }
 *
 *     &#064;Override
 *     public void onFinish() {
 *         // Completed the request (either success or failure)
 *     }
 * });
 * </pre>
 */
public abstract class TextHttpResponseHandler extends AsyncHttpResponseHandler {

    private static final String LOG_TAG = "TextHttpRH";

    /**
     * 使用默认的UTF-8编码创建新实例
     */
    public TextHttpResponseHandler() {
        this(DEFAULT_CHARSET);
    }

    /**
     * 用给定的字符串编码创建新的实例
     *
     * @param encoding 字符串编码, 查看 {@link #setCharset(String)}
     */
    public TextHttpResponseHandler(String encoding) {
        super();
        setCharset(encoding);
    }

    /**
     * 当请求失败时调用
     *
     * @param statusCode     http响应状态行
     * @param headers        响应头如果有的话
     * @param responseString 给定字符集的字符串响应
     * @param throwable      处理请求时返回的throwable
     */
    public abstract void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable);

    /**
     * 当请求成功时调用
     *
     * @param statusCode     http响应状态行
     * @param headers        响应头如果有的话
     * @param responseString 给定字符集的字符串响应
     */
    public abstract void onSuccess(int statusCode, Header[] headers, String responseString);

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBytes) {
        onSuccess(statusCode, headers, getResponseString(responseBytes, getCharset()));
    }

    @Override
    public void onFailure(int statusCode, Header[] headers, byte[] responseBytes, Throwable throwable) {
        onFailure(statusCode, headers, getResponseString(responseBytes, getCharset()), throwable);
    }

    /**
     * 尝试将响应字节编码为集合编码的字符串
     *
     * @param charset     字符集创建字符串
     * @param stringBytes 响应字节
     * @return 集合编码的字符串或null
     */
    public static String getResponseString(byte[] stringBytes, String charset) {
        try {
            String toReturn = (stringBytes == null) ? null : new String(stringBytes, charset);
            if (toReturn != null && toReturn.startsWith(UTF8_BOM)) {
                return toReturn.substring(1);
            }
            return toReturn;
        } catch (UnsupportedEncodingException e) {
            AsyncHttpClient.log.e(LOG_TAG, "Encoding response into string failed", e);
            return null;
        }
    }
}
