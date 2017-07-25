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

/*
    Some of the retry logic in this class is heavily borrowed from the
    fantastic droid-fu project: https://github.com/donnfelker/droid-fu
*/

package com.loopj.android.http;

import android.os.SystemClock;

import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;

import javax.net.ssl.SSLException;

/**
 * 请求重试处理器
 * 
 * @author lijian
 * @date 2017-7-25 下午11:08:52
 */
class RetryHandler implements HttpRequestRetryHandler {
	/** 例外白名单 */
    private final static HashSet<Class<?>> exceptionWhitelist = new HashSet<Class<?>>();
    /** 例外黑名单 */
    private final static HashSet<Class<?>> exceptionBlacklist = new HashSet<Class<?>>();

    static {
        // 如果服务器丢弃了我们的连接，请重试
        exceptionWhitelist.add(NoHttpResponseException.class);
        // 重试 - 这是因为它可能作为Wi-Fi到3G故障转移的一部分
        exceptionWhitelist.add(UnknownHostException.class);
        // 重试 - 这是因为它可能作为Wi-Fi到3G故障转移的一部分
        exceptionWhitelist.add(SocketException.class);

        // 从来没有重试超时
        exceptionBlacklist.add(InterruptedIOException.class);
        // 从不再次尝试SSL握手失败
        exceptionBlacklist.add(SSLException.class);
    }

    private final int maxRetries;// 最大重试次数
    private final int retrySleepTimeMS;// 重试休眠时间

    public RetryHandler(int maxRetries, int retrySleepTimeMS) {
        this.maxRetries = maxRetries;
        this.retrySleepTimeMS = retrySleepTimeMS;
    }

	/**
	 * 重试请求
	 * 
	 * @param exception 异常
	 * @param executionCount 执行次数
	 * @param context
	 */
    @Override
    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        boolean retry = true;

        Boolean b = (Boolean) context.getAttribute(ExecutionContext.HTTP_REQ_SENT);
        boolean sent = (b != null && b);

        if (executionCount > maxRetries) {
            // 如果超过最大重试次数，请勿重试
            retry = false;
        } else if (isInList(exceptionWhitelist, exception)) {
            // 如果错误列入白名单，请立即重试
            retry = true;
        } else if (isInList(exceptionBlacklist, exception)) {
            // 如果错误被列入黑名单，立即取消重试
            retry = false;
        } else if (!sent) {
            // 对于大多数其他错误，只有当请求尚未完全发送时才重试
            retry = true;
        }

        if (retry) {
            // 重新发送所有幂等请求
            HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
            if (currentReq == null) {
                return false;
            }
        }

        if (retry) {
            SystemClock.sleep(retrySleepTimeMS);
        } else {
            exception.printStackTrace();
        }

        return retry;
    }

    /**
     * 添加到白名单
     * 
     * @param cls
     */
    static void addClassToWhitelist(Class<?> cls) {
        exceptionWhitelist.add(cls);
    }

	/**
	 * 添加到黑名单
	 * 
	 * @param cls
	 */
    static void addClassToBlacklist(Class<?> cls) {
        exceptionBlacklist.add(cls);
    }

    /**
     * 判断是否在名单中(黑/白名单)
     * @param list 黑/白名单
     * @param error
     * @return
     */
    protected boolean isInList(HashSet<Class<?>> list, Throwable error) {
        for (Class<?> aList : list) {
        	// 测试给定的对象是否可以转换为由此类表示的类。 这是instanceof运算符的运行时版本。
            if (aList.isInstance(error)) {
                return true;
            }
        }
        return false;
    }
}
