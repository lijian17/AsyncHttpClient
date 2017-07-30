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
import org.apache.http.HttpStatus;

/**
 * 类用于与自定义JSON解析器（例如GSON或Jackson JSON）一起使用
 * <p>&nbsp;</p>
 * 应该覆盖{@link #parseResponse(String, boolean)}，并且必须返回一般的param类的类型，
 * 然后响应将被处理以实现抽象方法{@link #onSuccess(int, org.apache.http.Header[], String, Object)}或
 * {@link #onFailure(int, org.apache.http.Header[], Throwable, String, Object)}，取决于响应HTTP状态行（http结果码）
 *
 * @param <JSON_TYPE> 通用类型意图在回调中返回
 */
public abstract class BaseJsonHttpResponseHandler<JSON_TYPE> extends TextHttpResponseHandler {
    private static final String LOG_TAG = "BaseJsonHttpRH";

    /**
     * 使用默认字符集"UTF-8"创建一个新的JsonHttpResponseHandler
     */
    public BaseJsonHttpResponseHandler() {
        this(DEFAULT_CHARSET);
    }

    /**
     * 用给定的字符串编码创建一个新的JsonHttpResponseHandler
     *
     * @param encoding result string encoding, see <a href="https://docs.oracle.com/javase/7/docs/api/java/nio/charset/Charset.html">Charset</a>
     */
    public BaseJsonHttpResponseHandler(String encoding) {
        super(encoding);
    }

    /**
     * 基本抽象方法，处理定义的泛型类型
     *
     * @param statusCode      HTTP状态行
     * @param headers         响应headers
     * @param rawJsonResponse 相应字符串，不能为null
     * @param response        由{@link #parseResponse(String, boolean)}返回的响应
     */
    public abstract void onSuccess(int statusCode, Header[] headers, String rawJsonResponse, JSON_TYPE response);

    /**
     * 基本抽象方法，处理定义的泛型类型
     *
     * @param statusCode    HTTP状态行
     * @param headers       响应headers
     * @param throwable     处理请求时抛出错误
     * @param rawJsonData   原始字符串数据返回
     * @param errorResponse 由{@link #parseResponse(String, boolean)}返回的响应
     */
    public abstract void onFailure(int statusCode, Header[] headers, Throwable throwable, String rawJsonData, JSON_TYPE errorResponse);

    @Override
    public final void onSuccess(final int statusCode, final Header[] headers, final String responseString) {
        if (statusCode != HttpStatus.SC_NO_CONTENT) {
            Runnable parser = new Runnable() {
                @Override
                public void run() {
                    try {
                        final JSON_TYPE jsonResponse = parseResponse(responseString, false);
                        postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                onSuccess(statusCode, headers, responseString, jsonResponse);
                            }
                        });
                    } catch (final Throwable t) {
                        AsyncHttpClient.log.d(LOG_TAG, "parseResponse thrown an problem", t);
                        postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                onFailure(statusCode, headers, t, responseString, null);
                            }
                        });
                    }
                }
            };
            if (!getUseSynchronousMode() && !getUsePoolThread()) {
                new Thread(parser).start();
            } else {
                // In synchronous mode everything should be run on one thread
                parser.run();
            }
        } else {
            onSuccess(statusCode, headers, null, null);
        }
    }

    @Override
    public final void onFailure(final int statusCode, final Header[] headers, final String responseString, final Throwable throwable) {
        if (responseString != null) {
            Runnable parser = new Runnable() {
                @Override
                public void run() {
                    try {
                        final JSON_TYPE jsonResponse = parseResponse(responseString, true);
                        postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                onFailure(statusCode, headers, throwable, responseString, jsonResponse);
                            }
                        });
                    } catch (Throwable t) {
                        AsyncHttpClient.log.d(LOG_TAG, "parseResponse thrown an problem", t);
                        postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                onFailure(statusCode, headers, throwable, responseString, null);
                            }
                        });
                    }
                }
            };
            if (!getUseSynchronousMode() && !getUsePoolThread()) {
                new Thread(parser).start();
            } else {
                // 在同步模式下，一切都应该在一个线程上运行
                parser.run();
            }
        } else {
            onFailure(statusCode, headers, throwable, null, null);
        }
    }

    /**
     * 应该返回泛型类型的反序列化实例，可能会返回对象更多的模糊处理
     *
     * @param rawJsonData 响应字符串，可能为null
     * @param isFailure   指示是否从onFailure调用此方法
     * @return 通用类型的对象，如果选择，则为null
     * @throws Throwable允许您从反序列化JSON响应中抛出任何内容
     */
    protected abstract JSON_TYPE parseResponse(String rawJsonData, boolean isFailure) throws Throwable;
}
