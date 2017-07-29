package com.loopj.android.http;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

/**
 * ResponseHandlerInterface的空白实现，它忽略远程HTTP端点返回的所有内容，并丢弃所有各种日志消息
 * <p>&nbsp;</p>
 * 使用此实现，如果您故意想忽略所有响应，因为您不能将null ResponseHandlerInterface传递给AsyncHttpClient实现
 */
public class BlackholeHttpResponseHandler extends AsyncHttpResponseHandler {

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {

    }

    @Override
    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {

    }

    @Override
    public void onProgress(long bytesWritten, long totalSize) {

    }

    @Override
    public void onCancel() {

    }

    @Override
    public void onFinish() {

    }

    @Override
    public void onPostProcessResponse(ResponseHandlerInterface instance, HttpResponse response) {

    }

    @Override
    public void onPreProcessResponse(ResponseHandlerInterface instance, HttpResponse response) {

    }

    @Override
    public void onRetry(int retryNo) {

    }

    @Override
    public void onStart() {

    }

    @Override
    public void onUserException(Throwable error) {

    }
}
