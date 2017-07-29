/*
    Android Asynchronous Http Client
    Copyright (c) 2013 Jason Choy <jjwchoy@gmail.com>
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

import java.lang.ref.WeakReference;

/**
 * 一个AsyncRequest的句柄，可用于取消正在运行的请求。
 */
public class RequestHandle {
    private final WeakReference<AsyncHttpRequest> request;

    public RequestHandle(AsyncHttpRequest request) {
        this.request = new WeakReference<AsyncHttpRequest>(request);
    }

    /**
     * 尝试取消此请求。 如果请求已经完成，已经被取消或由于某种其他原因而无法取消，则此尝试将失败。 
     * 如果成功，并且在请求取消时该请求尚未启动，则此请求不应该运行。 
     * 如果请求已经启动，则mayInterruptIfRunning参数将确定执行该请求的线程是否应该被中断以试图停止请求。
     * <p>&nbsp;</p> 
     * 此方法返回后，对isDone()的后续调用将始终返回true。 
     * 如果此方法返回true，则对isCancelled()的后续调用将始终返回true。 
     * 如果请求被该方法取消，或者请求正常完成，则对isDone()的后续调用将返回true
     *
     * @param mayInterruptIfRunning 如果执行该请求的线程应该被中断，则为true; 否则，进行中的请求被允许完成
     * @return 如果请求无法取消，则为false，通常是因为已经正常完成; 否则的话
     */
    public boolean cancel(final boolean mayInterruptIfRunning) {
        final AsyncHttpRequest _request = request.get();
        if (_request != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        _request.cancel(mayInterruptIfRunning);
                    }
                }).start();
                // 在这一点上，如果请求已经立即取消，我们将不能可靠地告知我们将被取消
                return true;
            } else {
                return _request.cancel(mayInterruptIfRunning);
            }
        }
        return false;
    }

    /**
     * 如果此任务完成，则返回true。 完成可能是由于正常终止，异常或取消 - 在所有这些情况下，此方法将返回true。
     *
     * @return 如果此任务完成，则为true
     */
    public boolean isFinished() {
        AsyncHttpRequest _request = request.get();
        return _request == null || _request.isDone();
    }

    /**
     * 如果此任务在正常完成之前被取消，则返回true。
     *
     * @return 如果此任务在完成之前被取消，则为true
     */
    public boolean isCancelled() {
        AsyncHttpRequest _request = request.get();
        return _request == null || _request.isCancelled();
    }

    /**
     * 应该被垃圾收集
     * @return
     */
    public boolean shouldBeGarbageCollected() {
        boolean should = isCancelled() || isFinished();
        if (should)
            request.clear();
        return should;
    }

    /**
     * 将Object作为TAG设置为底层的AsyncHttpRequest
     *
     * @param tag 用作TAG的对象用于AsyncHttpRequest
     * @return this RequestHandle允许流体语法
     */
    public RequestHandle setTag(Object tag) {
        AsyncHttpRequest _request = request.get();
        if (_request != null)
            _request.setRequestTag(tag);
        return this;
    }

    /**
     * 如果还没有GCed，将返回底层AsyncHttpRequest的TAG
     *
     * @return 对象TAG，可以为空
     */
    public Object getTag() {
        AsyncHttpRequest _request = request.get();
        return _request == null ? null : _request.getTag();
    }
}