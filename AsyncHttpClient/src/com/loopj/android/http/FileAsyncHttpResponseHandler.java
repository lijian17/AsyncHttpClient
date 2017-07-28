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

import org.apache.http.Header;
import org.apache.http.HttpEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 文件异步Http响应Handler
 * 
 * @author lijian-pc
 * @date 2017-7-28 下午4:17:44
 */
public abstract class FileAsyncHttpResponseHandler extends AsyncHttpResponseHandler {

    protected final File file;
    protected final boolean append;
    protected final boolean renameIfExists;
    protected File frontendFile;
    private static final String LOG_TAG = "FileAsyncHttpRH";

    /**
     * 获取新的FileAsyncHttpResponseHandler并将响应存储在传递的文件中
     *
     * @param file 文件存储响应内部，不能为null
     */
    public FileAsyncHttpResponseHandler(File file) {
        this(file, false);
    }

    /**
     * 获取新的FileAsyncHttpResponseHandler并将响应存储在传递的文件中
     *
     * @param file 文件存储响应内部，不能为null
     * @param append 数据是否应附加到现有文件
     */
    public FileAsyncHttpResponseHandler(File file, boolean append) {
        this(file, append, false);
    }

    /**
     * 获取新的FileAsyncHttpResponseHandler并将响应存储在传递的文件中
     *
     * @param file 文件存储响应内部，不能为null
     * @param append 数据是否应附加到现有文件
     * @param renameTargetFileIfExists 目标文件是否应该重命名，如果它已经存在
     */
    public FileAsyncHttpResponseHandler(File file, boolean append, boolean renameTargetFileIfExists) {
        super();
        // 传入FileAsyncHttpResponseHandler构造函数的文件不能为null
        Utils.asserts(file != null, "File passed into FileAsyncHttpResponseHandler constructor must not be null");
        if (!file.isDirectory() && !file.getParentFile().isDirectory()) {
        	// 无法为请求的文件位置创建父目录
            Utils.asserts(file.getParentFile().mkdirs(), "Cannot create parent directories for requested File location");
        }
        if (file.isDirectory()) {
            if (!file.mkdirs()) {
            	// 无法为所请求的目录位置创建目录，可能不是问题
                AsyncHttpClient.log.d(LOG_TAG, "Cannot create directories for requested Directory location, might not be a problem");
            }
        }
        this.file = file;
        this.append = append;
        this.renameIfExists = renameTargetFileIfExists;
    }

    /**
     * 获取新的FileAsyncHttpResponseHandler与目标为临时文件的上下文
     *
     * @param context 上下文，不能为null
     */
    public FileAsyncHttpResponseHandler(Context context) {
        super();
        this.file = getTemporaryFile(context);
        this.append = false;
        this.renameIfExists = false;
    }

    /**
     * 尝试使用存储的响应删除文件
     *
     * @return 如果文件不存在或为空，则为false，如果成功删除，则为true
     */
    public boolean deleteTargetFile() {
        return getTargetFile() != null && getTargetFile().delete();
    }

    /**
     * 当调用构造函数时没有使用任何文件时使用
     *
     * @param context 上下文，不能为null
     * @return 临时文件，如果创建文件失败，则为null
     */
    protected File getTemporaryFile(Context context) {
    	// 尝试创建临时文件而不具有上下文
        Utils.asserts(context != null, "Tried creating temporary file without having Context");
        try {
            return File.createTempFile("temp_", "_handled", context.getCacheDir());
        } catch (IOException e) {
            AsyncHttpClient.log.e(LOG_TAG, "Cannot create temporary file", e);
        }
        return null;
    }

    /**
     * 检索其中存储响应的File对象
     * Retrieves File object in which the response is stored
     *
     * @return File 要保存响应的文件
     */
    protected File getOriginalFile() {
    	// 目标文件为空，致命！
        Utils.asserts(file != null, "Target file is null, fatal!");
        return file;
    }

    /**
     * 检索可能重命名后代表响应最终位置的文件
     *
     * @return File 最终目标文件
     */
    public File getTargetFile() {
        if (frontendFile == null) {
            frontendFile = getOriginalFile().isDirectory() ? getTargetFileByParsingURL() : getOriginalFile();
        }
        return frontendFile;
    }

    /**
     * 将在给定文件夹中返回表示最后一个URL段的文件实例。 <br>
     * 如果文件已经存在，并且renameTargetFileIfExists被设置为true，<br>
     * 将尝试查找不存在的文件，这种情况下的命名模板为"filename.ext" =&gt; "filename (%d).ext",或不带扩展名"filename" =&gt; "filename (%d)"
     *
     * @return 在给定目录中的文件，由最后一段请求URL构造
     */
    protected File getTargetFileByParsingURL() {
    	// 目标文件不是目录，无法继续
        Utils.asserts(getOriginalFile().isDirectory(), "Target file is not a directory, cannot proceed");
        // RequestURI为null，无法继续
        Utils.asserts(getRequestURI() != null, "RequestURI is null, cannot proceed");
        String requestURL = getRequestURI().toString();
        String filename = requestURL.substring(requestURL.lastIndexOf('/') + 1, requestURL.length());
        File targetFileRtn = new File(getOriginalFile(), filename);
        if (targetFileRtn.exists() && renameIfExists) {
            String format;
            if (!filename.contains(".")) {
                format = filename + " (%d)";
            } else {
                format = filename.substring(0, filename.lastIndexOf('.')) + " (%d)" + filename.substring(filename.lastIndexOf('.'), filename.length());
            }
            int index = 0;
            while (true) {
                targetFileRtn = new File(getOriginalFile(), String.format(format, index));
                if (!targetFileRtn.exists())
                    return targetFileRtn;
                index++;
            }
        }
        return targetFileRtn;
    }

    @Override
    public final void onFailure(int statusCode, Header[] headers, byte[] responseBytes, Throwable throwable) {
        onFailure(statusCode, headers, throwable, getTargetFile());
    }

    /**
     * 要覆盖的方法，尽可能多地接收文件当文件被认为是失败或在检索文件时是否有错误时被调用
     *
     * @param statusCode http文件状态行
     * @param headers    如果有文件http头
     * @param throwable  returned throwable
     * @param file       存储响应的文件
     */
    public abstract void onFailure(int statusCode, Header[] headers, Throwable throwable, File file);

    @Override
    public final void onSuccess(int statusCode, Header[] headers, byte[] responseBytes) {
        onSuccess(statusCode, headers, getTargetFile());
    }

    /**
     * 要被覆盖的方法尽可能多地收到响应
     *
     * @param statusCode http文件状态行
     * @param headers    如果有文件http头
     * @param file       存储响应的文件
     */
    public abstract void onSuccess(int statusCode, Header[] headers, File file);

    @Override
    protected byte[] getResponseData(HttpEntity entity) throws IOException {
        if (entity != null) {
            InputStream instream = entity.getContent();
            long contentLength = entity.getContentLength();
            FileOutputStream buffer = new FileOutputStream(getTargetFile(), this.append);
            if (instream != null) {
                try {
                    byte[] tmp = new byte[BUFFER_SIZE];
                    int l, count = 0;
                    // 如果请求已被取消，请不要发送消息
                    while ((l = instream.read(tmp)) != -1 && !Thread.currentThread().isInterrupted()) {
                        count += l;
                        buffer.write(tmp, 0, l);
                        sendProgressMessage(count, contentLength);
                    }
                } finally {
                    AsyncHttpClient.silentCloseInputStream(instream);
                    buffer.flush();
                    AsyncHttpClient.silentCloseOutputStream(buffer);
                }
            }
        }
        return null;
    }

}
