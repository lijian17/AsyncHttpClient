/*
    Android Asynchronous Http Client
    Copyright (c) 2013 Marek Sebera <marek.sebera@gmail.com>
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
import org.apache.http.HttpResponse;

import java.io.IOException;
import java.net.URI;

/**
 * 响应处理器接口（接口来实现标准化）
 */
public interface ResponseHandlerInterface {

    /**
     * 返回数据，请求是否成功完成
     *
     * @param response HttpResponse对象与数据
     * @throws java.io.IOException 如果从响应中检索数据失败
     */
    void sendResponseMessage(HttpResponse response) throws IOException;

    /**
     * 通知回调，该请求开始执行
     */
    void sendStartMessage();

    /**
     * 通知回调，该请求已完成并正在从线程池中删除
     */
    void sendFinishMessage();

    /**
     * 通知回调，该请求（主要是上传）已经进行
     *
     * @param bytesWritten 写入字节数
     * @param bytesTotal   要写入的总字节数
     */
    void sendProgressMessage(long bytesWritten, long bytesTotal);

    /**
     * 通知回调，该请求被取消
     */
    void sendCancelMessage();

    /**
     * 通知回调，该请求被成功处理
     *
     * @param statusCode   HTTP状态码
     * @param headers      返回请求头
     * @param responseBody 返回数据
     */
    void sendSuccessMessage(int statusCode, Header[] headers, byte[] responseBody);

    /**
     * 如果请求已完成，并返回错误代码或执行失败
     *
     * @param statusCode   返回HTTP状态码
     * @param headers      返回请求头
     * @param responseBody 返回数据
     * @param error        请求失败的原因
     */
    void sendFailureMessage(int statusCode, Header[] headers, byte[] responseBody, Throwable error);

    /**
     * 通知要求重试的回调
     *
     * @param retryNo 一个请求中的重试次数
     */
    void sendRetryMessage(int retryNo);

    /**
     * 返回用于请求的URI
     *
     * @return 起源请求的URI
     */
    URI getRequestURI();

    /**
     * 返回用于请求的Header[]
     *
     * @return 来自起始请求的请求头
     */
    Header[] getRequestHeaders();

    /**
     * 帮助处理程序接收请求URI信息
     *
     * @param requestURI 声明的请求URI
     */
    void setRequestURI(URI requestURI);

    /**
     * 帮助处理程序接收Header[]信息
     *
     * @param requestHeaders Headers声称是从原始请求
     */
    void setRequestHeaders(Header[] requestHeaders);

    /**
     * 可以设置，处理程序是否应该是异步的或同步的
     *
     * @param useSynchronousMode 数据是否应该在后台线程上处理UI线程
     */
    void setUseSynchronousMode(boolean useSynchronousMode);

    /**
     * 返回处理程序是异步还是同步
     *
     * @return 如果ResponseHandler以同步模式运行，则返回true
     */
    boolean getUseSynchronousMode();

    /**
     * 设置处理程序是否应该在池的线程或UI线程上执行
     *
     * @param usePoolThread 如果ResponseHandler应该在pool的线程上运行
     */
    void setUsePoolThread(boolean usePoolThread);

    /**
     * 返回处理程序是否应该在池的线程或UI线程上执行
     *
     * @return 如果ResponseHandler应该在pool的线程上运行，则为boolean
     */
    boolean getUsePoolThread();

    /**
     * 当系统即将处理响应时，系统会调用此方法一次。library确保单个响应仅被预处理一次。
     * <p>&nbsp;</p>
     * 请注意：预处理不会在主线程上运行，因此您必须执行的任何UI活动都应正确分派到应用程序的UI线程。
     *
     * @param 实例此响应对象的实例
     * @param 响应预处理的响应
     */
    void onPreProcessResponse(ResponseHandlerInterface instance, HttpResponse response);

    /**
     * 当请求完全发送，处理和完成时，系统会调用此方法一次。 该库确保单个响应仅被后处理一次。
     * <p>&nbsp;</p>
     * 请注意：后处理不会在主线程上运行，因此您必须执行的任何UI活动都应正确分派到应用程序的UI线程。
     *
     * @param 实例此响应对象的实例
     * @param 响应后处理的响应
     */
    void onPostProcessResponse(ResponseHandlerInterface instance, HttpResponse response);

    /**
     * 将TAG设置为ResponseHandlerInterface实现，然后可以在实现的方法中获取，例如onSuccess，onFailure，...
     *
     * @param TAG 要设置为TAG的对象将被放置在WeakReference中
     */
    void setTag(Object TAG);

    /**
     * 如果TAG对象尚未从内存中释放出来，将会检索
     *
     * @return 对象TAG，如果已被垃圾回收，则为null
     */
    Object getTag();
}
