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

import android.content.Context;

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;

/**
 * 在同步模式下处理http请求，因此您的呼叫者线程将在每个请求上被阻止
 *
 * @see com.loopj.android.http.AsyncHttpClient
 */
public class SyncHttpClient extends AsyncHttpClient {

    /**
     * 创建一个具有默认构造函数参数值的新SyncHttpClient
     */
    public SyncHttpClient() {
        super(false, 80, 443);
    }

    /**
     * 创建一个新的SyncHttpClient。
     *
     * @param httpPort 非标准的仅HTTP端口
     */
    public SyncHttpClient(int httpPort) {
        super(false, httpPort, 443);
    }

    /**
     * 创建一个新的SyncHttpClient。
     *
     * @param httpPort  非标准的仅HTTP端口
     * @param httpsPort 非标准HTTPS唯一端口
     */
    public SyncHttpClient(int httpPort, int httpsPort) {
        super(false, httpPort, httpsPort);
    }

    /**
     * 使用给定的参数创建新的synchttpclient
	 * 
	 * @param fixNoHttpResponseException 是否通过省略SSL验证来解决问题
	 * @param httpPort                   要使用的HTTP端口必须大于0。
	 * @param httpsPort                  要使用的HTTPS端口必须大于0
     */
    public SyncHttpClient(boolean fixNoHttpResponseException, int httpPort, int httpsPort) {
        super(fixNoHttpResponseException, httpPort, httpsPort);
    }

    /**
     * 创建一个新的SyncHttpClient。
     *
     * @param schemeRegistry SchemeRegistry要使用
     */
    public SyncHttpClient(SchemeRegistry schemeRegistry) {
        super(schemeRegistry);
    }

    @Override
    protected RequestHandle sendRequest(DefaultHttpClient client,
                                        HttpContext httpContext, HttpUriRequest uriRequest,
                                        String contentType, ResponseHandlerInterface responseHandler,
                                        Context context) {
        if (contentType != null) {
            uriRequest.addHeader(AsyncHttpClient.HEADER_CONTENT_TYPE, contentType);
        }

        responseHandler.setUseSynchronousMode(true);

		/*
         * 将直接执行请求
		*/
        newAsyncHttpRequest(client, httpContext, uriRequest, contentType, responseHandler, context).run();

        // 返回无法用于取消请求的请求句柄，因为它在返回时已经完成
        return new RequestHandle(null);
    }
}
