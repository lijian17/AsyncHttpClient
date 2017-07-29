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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.util.ByteArrayBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;

/**
 * 用于拦截和处理使用{@link AsyncHttpClient}的请求的响应。 
 * {@link #onSuccess(int, org.apache.http.Header[], byte[])}方法被设计为用您自己的响应处理代码进行匿名覆盖。
 * <p>&nbsp;</p>
 * 另外，您可以根据需要覆盖{@link #onFailure(int, org.apache.http.Header[], byte[], Throwable)}, {@link #onStart()}, 
 * {@link #onFinish()}, {@link #onRetry(int)} and {@link #onProgress(long, long)}方法。
 * 
 * <p>&nbsp;</p> For example: <p>&nbsp;</p>
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * client.get("https://www.baidu.com", new AsyncHttpResponseHandler() {
 *     &#064;Override
 *     public void onStart() {
 *         // Initiated the request
 *     }
 *
 *     &#064;Override
 *     public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
 *         // Successfully got a response
 *     }
 *
 *     &#064;Override
 *     public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
 *         // Response failed :(
 *     }
 *
 *     &#064;Override
 *     public void onRetry(int retryNo) {
 *         // Request was retried
 *     }
 *
 *     &#064;Override
 *     public void onProgress(long bytesWritten, long totalSize) {
 *         // Progress notification
 *     }
 *
 *     &#064;Override
 *     public void onFinish() {
 *         // Completed the request (either success or failure)
 *     }
 * });
 * </pre>
 */
public abstract class AsyncHttpResponseHandler implements ResponseHandlerInterface {
    private static final String LOG_TAG = "AsyncHttpRH";

    /** 消息-成功 */
    protected static final int SUCCESS_MESSAGE = 0;
    /** 消息-失败 */
    protected static final int FAILURE_MESSAGE = 1;
    /** 消息-开始 */
    protected static final int START_MESSAGE = 2;
    /** 消息-完成 */
    protected static final int FINISH_MESSAGE = 3;
    /** 消息-进度 */
    protected static final int PROGRESS_MESSAGE = 4;
    /** 消息-重试 */
    protected static final int RETRY_MESSAGE = 5;
    /** 消息-取消 */
    protected static final int CANCEL_MESSAGE = 6;

    /** 缓冲区大小 */
    protected static final int BUFFER_SIZE = 4096;

    /** 默认编码 */
    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final String UTF8_BOM = "\uFEFF";
    /** 响应编码 */
    private String responseCharset = DEFAULT_CHARSET;
    private Handler handler;
    /** 使用同步模式 */
    private boolean useSynchronousMode;
    /** 使用线程池 */
    private boolean usePoolThread;

    /** 请求URI */
    private URI requestURI = null;
    private Header[] requestHeaders = null;
    private Looper looper = null;
    private WeakReference<Object> TAG = new WeakReference<Object>(null);

    /**
     * 创建一个新的AsyncHttpResponseHandler
     */
    public AsyncHttpResponseHandler() {
        this(null);
    }

    /**
     * 使用用户提供的looper创建一个新的AsyncHttpResponseHandler。 
     * 如果传递的looper为null，则将使用附加到当前线程的looper。
     *
     * @param looper
     */
    public AsyncHttpResponseHandler(Looper looper) {
        this.looper = looper == null ? Looper.myLooper() : looper;

        // 默认使用异步模式。
        setUseSynchronousMode(false);

        // 默认情况下，不要使用线程池的线程来触发回调。
        setUsePoolThread(false);
    }

    /**
     * 创建一个新的AsyncHttpResponseHandler，并决定是否在当前线程池looper或池线程上触发回调。
     *
     * @param usePoolThread 是否使用池的线程来消除回调
     */
    public AsyncHttpResponseHandler(boolean usePoolThread) {
        // 是否使用池的线程来消除回调。
        setUsePoolThread(usePoolThread);

        // 当使用pool的线程时，有一个looper是没有意义的。
        if (!getUsePoolThread()) {
        	// 使用当前线程的looper
            this.looper = Looper.myLooper();

            // 默认使用异步模式。
            setUseSynchronousMode(false);
        }
    }

    @Override
    public void setTag(Object TAG) {
        this.TAG = new WeakReference<Object>(TAG);
    }

    @Override
    public Object getTag() {
        return this.TAG.get();
    }

    @Override
    public URI getRequestURI() {
        return this.requestURI;
    }

    @Override
    public Header[] getRequestHeaders() {
        return this.requestHeaders;
    }

    @Override
    public void setRequestURI(URI requestURI) {
        this.requestURI = requestURI;
    }

    @Override
    public void setRequestHeaders(Header[] requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    /**
     * 避免使用非匿名handler泄漏。
     */
    private static class ResponderHandler extends Handler {
        private final AsyncHttpResponseHandler mResponder;

        ResponderHandler(AsyncHttpResponseHandler mResponder, Looper looper) {
            super(looper);
            this.mResponder = mResponder;
        }

        @Override
        public void handleMessage(Message msg) {
            mResponder.handleMessage(msg);
        }
    }

    @Override
    public boolean getUseSynchronousMode() {
        return useSynchronousMode;
    }

    @Override
    public void setUseSynchronousMode(boolean sync) {
    	// 设置异步模式前必须先准备好一个looper
        if (!sync && looper == null) {
            sync = true;
            AsyncHttpClient.log.w(LOG_TAG, "Current thread has not called Looper.prepare(). Forcing synchronous mode.");
        }

        // 如果使用异步模式。
        if (!sync && handler == null) {
        	// 创建当前线程handler用于提交任务
            handler = new ResponderHandler(this, looper);
        } else if (sync && handler != null) {
            // TODO: 考虑添加一个标志来删除所有排队的消息。
            handler = null;
        }

        useSynchronousMode = sync;
    }

    @Override
    public boolean getUsePoolThread() {
        return usePoolThread;
    }

    @Override
    public void setUsePoolThread(boolean pool) {
    	// 如果要使用池线程，那么保存一个looper就没有必要了，也不需要一个handler。
        if (pool) {
            looper = null;
            handler = null;
        }

        usePoolThread = pool;
    }

    /**
     * 设置响应字符串的字符集。 如果未设置，则默认为UTF-8。
     *
     * @param charset
     * @see <a href="https://docs.oracle.com/javase/7/docs/api/java/nio/charset/Charset.html">Charset</a>
     */
    public void setCharset(final String charset) {
        this.responseCharset = charset;
    }

    public String getCharset() {
        return this.responseCharset == null ? DEFAULT_CHARSET : this.responseCharset;
    }

    /**
     * 当请求进度时触发，覆盖自己的代码来处理
     * Fired when the request progress, override to handle in your own code
     *
     * @param bytesWritten 从文件开始偏移
     * @param totalSize    文件总大小
     */
    public void onProgress(long bytesWritten, long totalSize) {
        AsyncHttpClient.log.v(LOG_TAG, String.format("Progress %d from %d (%2.0f%%)", bytesWritten, totalSize, (totalSize > 0) ? (bytesWritten * 1.0 / totalSize) * 100 : -1));
    }

    /**
     * 当请求启动时触发，覆盖自己的代码来处理
     */
    public void onStart() {
        // 默认的日志警告是不必要的，因为这个方法只是可选的通知
    }

    /**
     * 在请求完成后，在成功和失败之后，都会被触发，覆盖自己的代码来处理
     */
    public void onFinish() {
        // 默认的日志警告是不必要的，因为这个方法只是可选的通知
    }

    @Override
    public void onPreProcessResponse(ResponseHandlerInterface instance, HttpResponse response) {
        // 默认动作是什么都不做...
    }

    @Override
    public void onPostProcessResponse(ResponseHandlerInterface instance, HttpResponse response) {
        // 默认动作是什么都不做...
    }

    /**
     * 当请求成功返回时触发，重写以在您自己的代码中处理
     *
     * @param statusCode   响应的状态代码
     * @param headers      返回headers，如果有的话
     * @param responseBody 来自服务器的HTTP响应的正文
     */
    public abstract void onSuccess(int statusCode, Header[] headers, byte[] responseBody);

    /**
     * 当请求无法完成时触发，重写以处理您自己的代码
     *
     * @param statusCode   响应的状态代码
     * @param headers      返回headers，如果有的话
     * @param responseBody 来自服务器的HTTP响应的正文
     * @param error        失败的根本原因
     */
    public abstract void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error);

    /**
     * 发生重试时触发，重写以处理您自己的代码
     *
     * @param retryNo 重试次数
     */
    public void onRetry(int retryNo) {
        AsyncHttpClient.log.d(LOG_TAG, String.format("Request retry no. %d", retryNo));
    }

    public void onCancel() {
        AsyncHttpClient.log.d(LOG_TAG, "Request got cancelled");
    }

    public void onUserException(Throwable error) {
        AsyncHttpClient.log.e(LOG_TAG, "User-space exception detected!", error);
        throw new RuntimeException(error);
    }

    @Override
    final public void sendProgressMessage(long bytesWritten, long bytesTotal) {
        sendMessage(obtainMessage(PROGRESS_MESSAGE, new Object[]{bytesWritten, bytesTotal}));
    }

    @Override
    final public void sendSuccessMessage(int statusCode, Header[] headers, byte[] responseBytes) {
        sendMessage(obtainMessage(SUCCESS_MESSAGE, new Object[]{statusCode, headers, responseBytes}));
    }

    @Override
    final public void sendFailureMessage(int statusCode, Header[] headers, byte[] responseBody, Throwable throwable) {
        sendMessage(obtainMessage(FAILURE_MESSAGE, new Object[]{statusCode, headers, responseBody, throwable}));
    }

    @Override
    final public void sendStartMessage() {
        sendMessage(obtainMessage(START_MESSAGE, null));
    }

    @Override
    final public void sendFinishMessage() {
        sendMessage(obtainMessage(FINISH_MESSAGE, null));
    }

    @Override
    final public void sendRetryMessage(int retryNo) {
        sendMessage(obtainMessage(RETRY_MESSAGE, new Object[]{retryNo}));
    }

    @Override
    final public void sendCancelMessage() {
        sendMessage(obtainMessage(CANCEL_MESSAGE, null));
    }

    // 模拟android Handler和Message方法的方法
    protected void handleMessage(Message message) {
        Object[] response;

        try {
            switch (message.what) {
                case SUCCESS_MESSAGE:
                    response = (Object[]) message.obj;
                    if (response != null && response.length >= 3) {
                        onSuccess((Integer) response[0], (Header[]) response[1], (byte[]) response[2]);
                    } else {
                        AsyncHttpClient.log.e(LOG_TAG, "SUCCESS_MESSAGE didn't got enough params");
                    }
                    break;
                case FAILURE_MESSAGE:
                    response = (Object[]) message.obj;
                    if (response != null && response.length >= 4) {
                        onFailure((Integer) response[0], (Header[]) response[1], (byte[]) response[2], (Throwable) response[3]);
                    } else {
                        AsyncHttpClient.log.e(LOG_TAG, "FAILURE_MESSAGE didn't got enough params");
                    }
                    break;
                case START_MESSAGE:
                    onStart();
                    break;
                case FINISH_MESSAGE:
                    onFinish();
                    break;
                case PROGRESS_MESSAGE:
                    response = (Object[]) message.obj;
                    if (response != null && response.length >= 2) {
                        try {
                            onProgress((Long) response[0], (Long) response[1]);
                        } catch (Throwable t) {
                            AsyncHttpClient.log.e(LOG_TAG, "custom onProgress contains an error", t);
                        }
                    } else {
                        AsyncHttpClient.log.e(LOG_TAG, "PROGRESS_MESSAGE didn't got enough params");
                    }
                    break;
                case RETRY_MESSAGE:
                    response = (Object[]) message.obj;
                    if (response != null && response.length == 1) {
                        onRetry((Integer) response[0]);
                    } else {
                        AsyncHttpClient.log.e(LOG_TAG, "RETRY_MESSAGE didn't get enough params");
                    }
                    break;
                case CANCEL_MESSAGE:
                    onCancel();
                    break;
            }
        } catch (Throwable error) {
            onUserException(error);
        }
    }

    protected void sendMessage(Message msg) {
        if (getUseSynchronousMode() || handler == null) {
            handleMessage(msg);
        } else if (!Thread.currentThread().isInterrupted()) { // 如果请求已被取消，请不要发送消息
            Utils.asserts(handler != null, "handler should not be null!");
            handler.sendMessage(msg);
        }
    }

    /**
     * Helper方法将runnable发送到本地handler loop
     *
     * @param runnable runnable实例，可以为null
     */
    protected void postRunnable(Runnable runnable) {
        if (runnable != null) {
            if (getUseSynchronousMode() || handler == null) {
                // 该响应处理程序是同步的，在当前线程上运行
                runnable.run();
            } else {
            	// 否则，运行提供的handler
                handler.post(runnable);
            }
        }
    }

    /**
     * 从处理程序创建Message实例的Helper方法
     * Helper method to create Message instance from handler
     *
     * @param responseMessageId
     * @param responseMessageData
     * @return Message 实例，不应该为null
     */
    protected Message obtainMessage(int responseMessageId, Object responseMessageData) {
        return Message.obtain(handler, responseMessageId, responseMessageData);
    }

    @Override
    public void sendResponseMessage(HttpResponse response) throws IOException {
        // 如果请求被取消，请不要处理
        if (!Thread.currentThread().isInterrupted()) {
            StatusLine status = response.getStatusLine();
            byte[] responseBody;
            responseBody = getResponseData(response.getEntity());
            // 额外的取消检查作为getResponseData()可以非零时间处理
            if (!Thread.currentThread().isInterrupted()) {
                if (status.getStatusCode() >= 300) {
                    sendFailureMessage(status.getStatusCode(), response.getAllHeaders(), responseBody, new HttpResponseException(status.getStatusCode(), status.getReasonPhrase()));
                } else {
                    sendSuccessMessage(status.getStatusCode(), response.getAllHeaders(), responseBody);
                }
            }
        }
    }

    /**
     * 返回的字节数组响应HttpEntity内容
     *
     * @param entity 可以为null
     * @return 响应实体或null
     * @throws java.io.IOException 如果读取实体或创建字节数组失败
     */
    byte[] getResponseData(HttpEntity entity) throws IOException {
        byte[] responseBody = null;
        if (entity != null) {
            InputStream instream = entity.getContent();
            if (instream != null) {
                long contentLength = entity.getContentLength();
                if (contentLength > Integer.MAX_VALUE) {
                	// HTTP实体太大，无法缓冲在内存中
                    throw new IllegalArgumentException("HTTP entity too large to be buffered in memory");
                }
                int buffersize = (contentLength <= 0) ? BUFFER_SIZE : (int) contentLength;
                try {
                    ByteArrayBuffer buffer = new ByteArrayBuffer(buffersize);
                    try {
                        byte[] tmp = new byte[BUFFER_SIZE];
                        long count = 0;
                        int l;
                        // 如果请求已被取消，请不要发送消息
                        while ((l = instream.read(tmp)) != -1 && !Thread.currentThread().isInterrupted()) {
                            count += l;
                            buffer.append(tmp, 0, l);
                            sendProgressMessage(count, (contentLength <= 0 ? 1 : contentLength));
                        }
                    } finally {
                        AsyncHttpClient.silentCloseInputStream(instream);
                        AsyncHttpClient.endEntityViaReflection(entity);
                    }
                    responseBody = buffer.toByteArray();
                } catch (OutOfMemoryError e) {
                    System.gc();
                    throw new IOException("File too large to fit into available memory");
                }
            }
        }
        return responseBody;
    }
}
