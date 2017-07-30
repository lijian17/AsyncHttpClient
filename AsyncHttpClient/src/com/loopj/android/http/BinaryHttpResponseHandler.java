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

import android.os.Looper;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 用于拦截和处理使用{@link AsyncHttpClient}的请求的响应。
 * 接收具有内容类型白名单的字节数组的响应正文。 （例如，根据允许列表检查Content-Type，Content-length）。
 * <p>&nbsp;</p> For example: <p>&nbsp;</p>
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * String[] allowedTypes = new String[] { "image/png" };
 * client.get("https://www.example.com/image.png", new BinaryHttpResponseHandler(allowedTypes) {
 *     &#064;Override
 *     public void onSuccess(byte[] imageData) {
 *         // Successfully got a response
 *     }
 *
 *     &#064;Override
 *     public void onFailure(Throwable e, byte[] imageData) {
 *         // Response failed :(
 *     }
 * });
 * </pre>
 */
public abstract class BinaryHttpResponseHandler extends AsyncHttpResponseHandler {

    private static final String LOG_TAG = "BinaryHttpRH";

    /** 允许的Content-Type */
    private String[] mAllowedContentTypes = new String[]{
            RequestParams.APPLICATION_OCTET_STREAM,
            "image/jpeg",
            "image/png",
            "image/gif"
    };

    /**
     * 可以覆盖方法来返回允许的内容类型，有时可以比在构造函数中传递数据更好
     *
     * @return content-types数组和模式字符串模板 (eg. '.*' 以匹配每个响应)
     */
    public String[] getAllowedContentTypes() {
        return mAllowedContentTypes;
    }

    /**
     * 创建一个新的BinaryHttpResponseHandler
     */
    public BinaryHttpResponseHandler() {
        super();
    }

    /**
     * 创建一个新的BinaryHttpResponseHandler，并覆盖使用传递的String数组（希望）内容类型的默认允许的内容类型。
     *
     * @param allowedContentTypes content types array, eg. 'image/jpeg' or pattern '.*'
     */
    public BinaryHttpResponseHandler(String[] allowedContentTypes) {
        super();
        if (allowedContentTypes != null) {
            mAllowedContentTypes = allowedContentTypes;
        } else {
            AsyncHttpClient.log.e(LOG_TAG, "Constructor passed allowedContentTypes was null !");
        }
    }
    
    /**
     * 使用用户提供的触发器创建一个新的BinaryHttpResponseHandler，并覆盖使用传递的String数组（希望）内容类型的默认允许的内容类型。
     *
     * @param allowedContentTypes content types array, eg. 'image/jpeg' or pattern '.*'
     * @param looper The looper to work with
     */
    public BinaryHttpResponseHandler(String[] allowedContentTypes, Looper looper) {
        super(looper);
        if (allowedContentTypes != null) {
            mAllowedContentTypes = allowedContentTypes;
        } else {
            AsyncHttpClient.log.e(LOG_TAG, "Constructor passed allowedContentTypes was null !");
        }
    }

    @Override
    public abstract void onSuccess(int statusCode, Header[] headers, byte[] binaryData);

    @Override
    public abstract void onFailure(int statusCode, Header[] headers, byte[] binaryData, Throwable error);

    @Override
    public final void sendResponseMessage(HttpResponse response) throws IOException {
        StatusLine status = response.getStatusLine();
        Header[] contentTypeHeaders = response.getHeaders(AsyncHttpClient.HEADER_CONTENT_TYPE);
        if (contentTypeHeaders.length != 1) {
            //malformed/ambiguous HTTP Header, ABORT!
            sendFailureMessage(
                status.getStatusCode(),
                response.getAllHeaders(),
                null,
                new HttpResponseException(
                    status.getStatusCode(),
                    "None, or more than one, Content-Type Header found!"
                )
            );
            return;
        }
        Header contentTypeHeader = contentTypeHeaders[0];
        boolean foundAllowedContentType = false;
        for (String anAllowedContentType : getAllowedContentTypes()) {
            try {
                if (Pattern.matches(anAllowedContentType, contentTypeHeader.getValue())) {
                    foundAllowedContentType = true;
                }
            } catch (PatternSyntaxException e) {
                AsyncHttpClient.log.e(LOG_TAG, "Given pattern is not valid: " + anAllowedContentType, e);
            }
        }
        if (!foundAllowedContentType) {
            //Content-Type在列表中未匹配到, 舍弃!
            sendFailureMessage(
                status.getStatusCode(),
                response.getAllHeaders(),
                null,
                new HttpResponseException(
                    status.getStatusCode(),
                    "Content-Type (" + contentTypeHeader.getValue() + ") not allowed!"
                )
            );
            return;
        }
        super.sendResponseMessage(response);
    }
}
