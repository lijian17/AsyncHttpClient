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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * 用于拦截和处理使用{@link AsyncHttpClient}的请求的响应，并自动解析为{@link JSONObject}或{@link JSONArray}。
 * 
 * <p>&nbsp;</p>
 * 这个类被设计为通过get, post, put 和 delete请求，匿名覆盖{@link #onSuccess(int, org.apache.http.Header[], org.json.JSONArray)} 和
 * {@link #onSuccess(int, org.apache.http.Header[], org.json.JSONObject)}获取数据
 * 
 * <p>&nbsp;</p>
 * 此外，您可以从父类覆盖其他事件方法。
 */
public class JsonHttpResponseHandler extends TextHttpResponseHandler {

    private static final String LOG_TAG = "JsonHttpRH";


    private boolean useRFC5179CompatibilityMode = true;

    /**
     * 使用默认的UTF-8编码JSON String创建一个新的JsonHttpResponseHandler
     */
    public JsonHttpResponseHandler() {
        super(DEFAULT_CHARSET);
    }

    /**
     * 使用指定的编码JSON String创建一个新的JsonHttpResponseHandler
     *
     * @param encoding 解析JSON时要使用的字符串编码
     */
    public JsonHttpResponseHandler(String encoding) {
        super(encoding);
    }

    /**
     * 使用默认的UTF-8编码JSON String创建一个新的JsonHttpResponseHandler并给出RFC5179CompatibilityMode
     *
     * @param useRFC5179CompatibilityMode Boolean mode to use RFC5179 or latest
     */
    public JsonHttpResponseHandler(boolean useRFC5179CompatibilityMode) {
        super(DEFAULT_CHARSET);
        this.useRFC5179CompatibilityMode = useRFC5179CompatibilityMode;
    }

    /**
     * 使用指定的编码JSON String创建一个新的JsonHttpResponseHandler并给出RFC5179CompatibilityMode
     *
     * @param encoding 解析JSON时要使用的字符串编码
     * @param useRFC5179CompatibilityMode Boolean mode to use RFC5179 or latest
     */
    public JsonHttpResponseHandler(String encoding, boolean useRFC5179CompatibilityMode) {
        super(encoding);
        this.useRFC5179CompatibilityMode = useRFC5179CompatibilityMode;
    }

    /**
     * 请求成功时返回
     *
     * @param statusCode http响应状态行
     * @param headers    响应头如果有的话
     * @param response   解析了如果有的话
     */
    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
        AsyncHttpClient.log.w(LOG_TAG, "onSuccess(int, Header[], JSONObject) was not overriden, but callback was received");
    }

    /**
     * 请求成功时返回
     *
     * @param statusCode http响应状态行
     * @param headers    响应头如果有的话
     * @param response   解析了如果有的话
     */
    public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
        AsyncHttpClient.log.w(LOG_TAG, "onSuccess(int, Header[], JSONArray) was not overriden, but callback was received");
    }

    /**
     * 请求失败时返回
     *
     * @param statusCode http响应状态行
     * @param headers    响应头如果有的话
     * @param throwable     描述请求失败的方式
     * @param errorResponse 解析了如果有的话
     */
    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
        AsyncHttpClient.log.w(LOG_TAG, "onFailure(int, Header[], Throwable, JSONObject) was not overriden, but callback was received", throwable);
    }

    /**
     * 请求失败时返回
     *
     * @param statusCode http响应状态行
     * @param headers    响应头如果有的话
     * @param throwable     描述请求失败的方式
     * @param errorResponse 解析了如果有的话
     */
    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
        AsyncHttpClient.log.w(LOG_TAG, "onFailure(int, Header[], Throwable, JSONArray) was not overriden, but callback was received", throwable);
    }

    @Override
    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
        AsyncHttpClient.log.w(LOG_TAG, "onFailure(int, Header[], String, Throwable) was not overriden, but callback was received", throwable);
    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, String responseString) {
        AsyncHttpClient.log.w(LOG_TAG, "onSuccess(int, Header[], String) was not overriden, but callback was received");
    }

    @Override
    public final void onSuccess(final int statusCode, final Header[] headers, final byte[] responseBytes) {
        if (statusCode != HttpStatus.SC_NO_CONTENT) {
            Runnable parser = new Runnable() {
                @Override
                public void run() {
                    try {
                        final Object jsonResponse = parseResponse(responseBytes);
                        postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                // 在RFC5179中，null不是有效的JSON
                                if (!useRFC5179CompatibilityMode && jsonResponse == null) {
                                    onSuccess(statusCode, headers, (String) null);
                                } else if (jsonResponse instanceof JSONObject) {
                                    onSuccess(statusCode, headers, (JSONObject) jsonResponse);
                                } else if (jsonResponse instanceof JSONArray) {
                                    onSuccess(statusCode, headers, (JSONArray) jsonResponse);
                                } else if (jsonResponse instanceof String) {
                                    // 在RFC5179中，一个简单的字符串值不是有效的JSON
                                    if (useRFC5179CompatibilityMode) {
                                        onFailure(statusCode, headers, (String) jsonResponse, new JSONException("Response cannot be parsed as JSON data"));
                                    } else {
                                        onSuccess(statusCode, headers, (String) jsonResponse);
                                    }
                                } else {
                                    onFailure(statusCode, headers, new JSONException("Unexpected response type " + jsonResponse.getClass().getName()), (JSONObject) null);
                                }
                            }
                        });
                    } catch (final JSONException ex) {
                        postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                onFailure(statusCode, headers, ex, (JSONObject) null);
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
            onSuccess(statusCode, headers, new JSONObject());
        }
    }

    @Override
    public final void onFailure(final int statusCode, final Header[] headers, final byte[] responseBytes, final Throwable throwable) {
        if (responseBytes != null) {
            Runnable parser = new Runnable() {
                @Override
                public void run() {
                    try {
                        final Object jsonResponse = parseResponse(responseBytes);
                        postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                // 在RFC5179中，null不是有效的JSON
                                if (!useRFC5179CompatibilityMode && jsonResponse == null) {
                                    onFailure(statusCode, headers, (String) null, throwable);
                                } else if (jsonResponse instanceof JSONObject) {
                                    onFailure(statusCode, headers, throwable, (JSONObject) jsonResponse);
                                } else if (jsonResponse instanceof JSONArray) {
                                    onFailure(statusCode, headers, throwable, (JSONArray) jsonResponse);
                                } else if (jsonResponse instanceof String) {
                                    onFailure(statusCode, headers, (String) jsonResponse, throwable);
                                } else {
                                    onFailure(statusCode, headers, new JSONException("Unexpected response type " + jsonResponse.getClass().getName()), (JSONObject) null);
                                }
                            }
                        });

                    } catch (final JSONException ex) {
                        postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                onFailure(statusCode, headers, ex, (JSONObject) null);
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
            AsyncHttpClient.log.v(LOG_TAG, "response body is null, calling onFailure(Throwable, JSONObject)");
            onFailure(statusCode, headers, throwable, (JSONObject) null);
        }
    }

    /**
     * 返回类型为{@link JSONObject}, {@link JSONArray}, String, Boolean, Integer, Long, 
     * Double 或 {@link JSONObject#NULL}的对象，请参阅{@link org.json.JSONTokener#nextValue()}
     * 
     * @param responseBody 响应字节在String中组合并解析为JSON
     * @return Object parsedResponse
     * @throws org.json.JSONException 在解析JSON时抛出异常
     */
    protected Object parseResponse(byte[] responseBody) throws JSONException {
        if (null == responseBody)
            return null;
        Object result = null;
        // 修剪字符串以防止以空白开头，并测试字符串是否有效的JSON，因为解析器不这样做:(如果JSON无效，则返回null
        String jsonString = getResponseString(responseBody, getCharset());
        if (jsonString != null) {
            jsonString = jsonString.trim();
            if (useRFC5179CompatibilityMode) {
                if (jsonString.startsWith("{") || jsonString.startsWith("[")) {
                    result = new JSONTokener(jsonString).nextValue();
                }
            } else {
                // 检查字符串是否是以JSONObject的{}样式 or JSONArray的[]样式
                // 如果不是，我们认为这是一个字符串
                if ((jsonString.startsWith("{") && jsonString.endsWith("}"))
                        || jsonString.startsWith("[") && jsonString.endsWith("]")) {
                    result = new JSONTokener(jsonString).nextValue();
                }
                // 检查这是否是一个字符串“我的字符串值”，并删除引号其他值类型（数值，布尔值）应该没有引号。
                else if (jsonString.startsWith("\"") && jsonString.endsWith("\"")) {
                    result = jsonString.substring(1, jsonString.length() - 1);
                }
            }
        }
        if (result == null) {
            result = jsonString;
        }
        return result;
    }

    public boolean isUseRFC5179CompatibilityMode() {
        return useRFC5179CompatibilityMode;
    }

    public void setUseRFC5179CompatibilityMode(boolean useRFC5179CompatibilityMode) {
        this.useRFC5179CompatibilityMode = useRFC5179CompatibilityMode;
    }

}
