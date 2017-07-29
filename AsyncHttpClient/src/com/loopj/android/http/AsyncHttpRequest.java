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

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内部类，代表Http请求，完成了initude的方式
 */
public class AsyncHttpRequest implements Runnable {
    private final AbstractHttpClient client;
    private final HttpContext context;
    private final HttpUriRequest request;
    private final ResponseHandlerInterface responseHandler;
    private int executionCount;
    private final AtomicBoolean isCancelled = new AtomicBoolean();
    /** 任务取消是否已发送通知标记 */
    private boolean cancelIsNotified;
    /** 标记请求已发送完成 */
    private volatile boolean isFinished;
    /** 是请求预处理 */
    private boolean isRequestPreProcessed;

    public AsyncHttpRequest(AbstractHttpClient client, HttpContext context, HttpUriRequest request, ResponseHandlerInterface responseHandler) {
        this.client = Utils.notNull(client, "client");
        this.context = Utils.notNull(context, "context");
        this.request = Utils.notNull(request, "request");
        this.responseHandler = Utils.notNull(responseHandler, "responseHandler");
    }

    /**
     * 当系统要处理请求时，系统会调用此方法。 该库确保单个请求只被预处理一次。
     * <p>&nbsp;</p>
     * 请注意：预处理不会在主线程上运行，因此您必须执行的任何UI活动都应正确分派到应用程序的UI线程。
     *
     * @param request 预处理请求
     */
    public void onPreProcessRequest(AsyncHttpRequest request) {
        // 默认动作是什么都不做...
    }

    /**
     * 当请求完全发送，处理和完成时，系统会调用此方法一次。 library确保单个请求仅被后处理一次。
     * <p>&nbsp;</p>
     * 请注意：后处理不会在主线程上运行，因此您必须执行的任何UI活动都应正确分派到应用程序的UI线程。
     *
     * @param request 预处理请求
     */
    public void onPostProcessRequest(AsyncHttpRequest request) {
        // 默认动作是什么都不做...
    }

    @Override
    public void run() {
        if (isCancelled()) {
            return;
        }

        // 仅对此请求执行一次预处理。
        if (!isRequestPreProcessed) {
            isRequestPreProcessed = true;
            onPreProcessRequest(this);
        }

        if (isCancelled()) {
            return;
        }

        responseHandler.sendStartMessage();

        if (isCancelled()) {
            return;
        }

        try {
            makeRequestWithRetries();
        } catch (IOException e) {
            if (!isCancelled()) {
                responseHandler.sendFailureMessage(0, null, null, e);
            } else {
                AsyncHttpClient.log.e("AsyncHttpRequest", "makeRequestWithRetries returned error", e);
            }
        }

        if (isCancelled()) {
            return;
        }

        responseHandler.sendFinishMessage();

        if (isCancelled()) {
            return;
        }

        // 对此请求进行后期处理。
        onPostProcessRequest(this);

        isFinished = true;
    }

    /**
     * 创建一个请求
     * 
     * @throws IOException
     */
    private void makeRequest() throws IOException {
        if (isCancelled()) {
            return;
        }

        // Fixes #115
        if (request.getURI().getScheme() == null) {
            // IOException的子类在调用者中处理
            throw new MalformedURLException("No valid URI scheme was provided");
        }

        if (responseHandler instanceof RangeFileAsyncHttpResponseHandler) {
            ((RangeFileAsyncHttpResponseHandler) responseHandler).updateRequestHeaders(request);
        }

        HttpResponse response = client.execute(request, context);

        if (isCancelled()) {
            return;
        }

        // 对此回应进行预处理。
        responseHandler.onPreProcessResponse(responseHandler, response);

        if (isCancelled()) {
            return;
        }

        // 响应准备就绪，处理它。
        responseHandler.sendResponseMessage(response);

        if (isCancelled()) {
            return;
        }

        // 对此回应进行后期处理。
        responseHandler.onPostProcessResponse(responseHandler, response);
    }

    /**
     * 请求重试
     * 
     * @throws IOException
     */
    private void makeRequestWithRetries() throws IOException {
        boolean retry = true;
        IOException cause = null;
        HttpRequestRetryHandler retryHandler = client.getHttpRequestRetryHandler();
        try {
            while (retry) {
                try {
                    makeRequest();
                    return;
                } catch (UnknownHostException e) {
                    // WI-FI和移动数据网络之间的切换可能导致重试，然后在WI-FI初始化时会导致UnknownHostException。 
                	// 重试逻辑将在这里被调用，如果这不是第一次重试（以协助未知主机的真实情况），这似乎比彻底失败
                    cause = new IOException("UnknownHostException exception: " + e.getMessage());
                    retry = (executionCount > 0) && retryHandler.retryRequest(e, ++executionCount, context);
                } catch (NullPointerException e) {
                    // 在HttpClient 4.0.x中有一个错误，在某些情况下会导致DefaultRequestExecutor抛出一个NPE, 
                	// see https://code.google.com/p/android/issues/detail?id=5255
                    cause = new IOException("NPE in HttpClient: " + e.getMessage());
                    retry = retryHandler.retryRequest(cause, ++executionCount, context);
                } catch (IOException e) {
                    if (isCancelled()) {
                        // 由于请求已被取消，因此异常
                        return;
                    }
                    cause = e;
                    retry = retryHandler.retryRequest(cause, ++executionCount, context);
                }
                if (retry) {
                    responseHandler.sendRetryMessage(executionCount);
                }
            }
        } catch (Exception e) {
            // 捕获任何其他东西，以确保故障消息传播
            AsyncHttpClient.log.e("AsyncHttpRequest", "Unhandled exception origin cause", e);
            cause = new IOException("Unhandled exception: " + e.getMessage());
        }

        // 清理掉抛出IOException
        throw (cause);
    }

    /**
     * 是否取消的
     * 
     * @return
     */
    public boolean isCancelled() {
        boolean cancelled = isCancelled.get();
        if (cancelled) {
            sendCancelNotification();
        }
        return cancelled;
    }

    /**
     * 发送取消通知
     */
    private synchronized void sendCancelNotification() {
        if (!isFinished && isCancelled.get() && !cancelIsNotified) {
            cancelIsNotified = true;
            responseHandler.sendCancelMessage();
        }
    }

    public boolean isDone() {
        return isCancelled() || isFinished;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        isCancelled.set(true);
        request.abort();
        return isCancelled();
    }

    /**
     * 将该对象设置为TAG，由WeakReference包装
     *
     * @param TAG 用作此RequestHandle的TAG的对象
     * @return this AsyncHttpRequest允许流体语法
     */
    public AsyncHttpRequest setRequestTag(Object TAG) {
        this.responseHandler.setTag(TAG);
        return this;
    }

    /**
     * 将返回此AsyncHttpRequest的TAG
     *
     * @return Object TAG，可以为null，如果已经是垃圾回收
     */
    public Object getTag() {
        return this.responseHandler.getTag();
    }
}
