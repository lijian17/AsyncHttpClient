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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

/**
 * 与{@link AsyncHttpClient}实例发出的请求一起发送的字符串请求参数或文件的集合。
 * <p>&nbsp;</p> For example: <p>&nbsp;</p>
 * <pre>
 * RequestParams params = new RequestParams();
 * params.put("username", "lijian");
 * params.put("password", "123456");
 * params.put("email", "my&#064;email.com");
 * params.put("profile_picture", new File("pic.jpg")); // Upload a File
 * params.put("profile_picture2", someInputStream); // Upload an InputStream
 * params.put("profile_picture3", new ByteArrayInputStream(someBytes)); // Upload some bytes
 *
 * Map&lt;String, String&gt; map = new HashMap&lt;String, String&gt;();
 * map.put("first_name", "James");
 * map.put("last_name", "Smith");
 * params.put("user", map); // url params: "user[first_name]=James&amp;user[last_name]=Smith"
 *
 * Set&lt;String&gt; set = new HashSet&lt;String&gt;(); // unordered collection
 * set.add("music");
 * set.add("art");
 * params.put("like", set); // url params: "like=music&amp;like=art"
 *
 * List&lt;String&gt; list = new ArrayList&lt;String&gt;(); // Ordered collection
 * list.add("Java");
 * list.add("C");
 * params.put("languages", list); // url params: "languages[0]=Java&amp;languages[1]=C"
 *
 * String[] colors = { "blue", "yellow" }; // Ordered collection
 * params.put("colors", colors); // url params: "colors[0]=blue&amp;colors[1]=yellow"
 *
 * File[] files = { new File("pic.jpg"), new File("pic1.jpg") }; // Ordered collection
 * params.put("files", files); // url params: "files[]=pic.jpg&amp;files[]=pic1.jpg"
 *
 * List&lt;Map&lt;String, String&gt;&gt; listOfMaps = new ArrayList&lt;Map&lt;String,
 * String&gt;&gt;();
 * Map&lt;String, String&gt; user1 = new HashMap&lt;String, String&gt;();
 * user1.put("age", "30");
 * user1.put("gender", "male");
 * Map&lt;String, String&gt; user2 = new HashMap&lt;String, String&gt;();
 * user2.put("age", "25");
 * user2.put("gender", "female");
 * listOfMaps.add(user1);
 * listOfMaps.add(user2);
 * params.put("users", listOfMaps); // url params: "users[][age]=30&amp;users[][gender]=male&amp;users[][age]=25&amp;users[][gender]=female"
 *
 * AsyncHttpClient client = new AsyncHttpClient();
 * client.post("https://myendpoint.com", params, responseHandler);
 * </pre>
 */
@SuppressWarnings("all")
public class RequestParams implements Serializable {
	private static final long serialVersionUID = 1L;

	public final static String APPLICATION_OCTET_STREAM =
            "application/octet-stream";

    public final static String APPLICATION_JSON =
            "application/json";

    protected final static String LOG_TAG = "RequestParams";
    protected boolean isRepeatable;
    protected boolean forceMultipartEntity = false;// 强制多部分实体
    protected boolean useJsonStreamer;
    protected String elapsedFieldInJsonStreamer = "_elapsed";
    protected boolean autoCloseInputStreams;
    protected final ConcurrentHashMap<String, String> urlParams = new ConcurrentHashMap<String, String>();
    protected final ConcurrentHashMap<String, StreamWrapper> streamParams = new ConcurrentHashMap<String, StreamWrapper>();
    protected final ConcurrentHashMap<String, FileWrapper> fileParams = new ConcurrentHashMap<String, FileWrapper>();
    protected final ConcurrentHashMap<String, List<FileWrapper>> fileArrayParams = new ConcurrentHashMap<String, List<FileWrapper>>();
    protected final ConcurrentHashMap<String, Object> urlParamsWithObjects = new ConcurrentHashMap<String, Object>();
    protected String contentEncoding = HTTP.UTF_8;

    /**
     * 设置{@link #getParamString()}和{@link #createFormEntity()}的返回值的内容编码<br>
     * 默认编码是"UTF-8"
     *
     * @param encoding 来自{@link HTTP}的字符串常量
     */
    public void setContentEncoding(final String encoding) {
        if (encoding != null) {
            this.contentEncoding = encoding;
        } else {
            AsyncHttpClient.log.d(LOG_TAG, "setContentEncoding called with null attribute");
        }
    }

    /**
     * 如果设置为true，即使没有文件或流被发送，也会将Content-Type请求头强制为"multipart/form-data"<br>
     * 默认false
     *
     * @param force
     */
    public void setForceMultipartEntityContentType(boolean force) {
        this.forceMultipartEntity = force;
    }

    /**
     * 构造一个新的空的{@code RequestParams}实例。
     */
    public RequestParams() {
        this((Map<String, String>) null);
    }

    /**
     * 构造一个新的RequestParams实例，该实例包含指定map中的key/value字符串参数。
     *
     * @param source
     */
    public RequestParams(Map<String, String> source) {
        if (source != null) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 构造一个新的RequestParams实例，并使用单个初始key/value字符串参数填充它。
     *
     * @param key
     * @param value
     */
	public RequestParams(final String key, final String value) {
        this(new HashMap<String, String>() {{
            put(key, value);
        }});
    }

    /**
     * 构造一个新的RequestParams实例，并使用多个初始key/value字符串参数填充它。
     *
     * @param keysAndValues 一系列键和值。 对象将自动转换为字符串（包括值为null）。
     * a sequence of keys and values. Objects are automatically converted to
     *                      Strings (including the value {@code null}).
     * @throws IllegalArgumentException 如果参数的个数不是成对出现抛异常
     */
    public RequestParams(Object... keysAndValues) {
        int len = keysAndValues.length;
        if (len % 2 != 0)
            throw new IllegalArgumentException("Supplied arguments must be even");
        for (int i = 0; i < len; i += 2) {
            String key = String.valueOf(keysAndValues[i]);
            String val = String.valueOf(keysAndValues[i + 1]);
            put(key, val);
        }
    }

    /**
     * 向请求添加key/value字符串对
     *
     * @param key
     * @param value
     */
    public void put(String key, String value) {
        if (key != null && value != null) {
            urlParams.put(key, value);
        }
    }

    /**
     * 将文件数组添加到请求中。
     *
     * @param key
     * @param files
     * @throws FileNotFoundException 如果在将请求数据组合到请求时没有找到传递的文件之一
     */
    public void put(String key, File files[]) throws FileNotFoundException {
        put(key, files, null, null);
    }

    /**
     * 使用自定义提供的文件content-type和文件名称将文件数组添加到请求
     *
     * @param key            新参数的键名称。
     * @param files          要添加的文件数组。
     * @param contentType    文件的内容类型，例如:application/json
     * @param customFileName 使用文件名而不是真正的文件名
     * @throws FileNotFoundException 将引发错误的文件参数传递
     */
    public void put(String key, File files[], String contentType, String customFileName) throws FileNotFoundException {

        if (key != null) {
            List<FileWrapper> fileWrappers = new ArrayList<FileWrapper>();
            for (File file : files) {
                if (file == null || !file.exists()) {
                    throw new FileNotFoundException();
                }
                fileWrappers.add(new FileWrapper(file, contentType, customFileName));
            }
            fileArrayParams.put(key, fileWrappers);
        }
    }

    /**
     * 向请求添加文件。
     *
     * @param key
     * @param file
     * @throws FileNotFoundException
     */
    public void put(String key, File file) throws FileNotFoundException {
        put(key, file, null, null);
    }

    /**
     * 使用自定义提供的文件名将文件添加到请求
     *
     * @param key
     * @param file
     * @param customFileName 使用文件名而不是真正的文件名
     * @throws FileNotFoundException
     */
    public void put(String key, String customFileName, File file) throws FileNotFoundException {
        put(key, file, null, customFileName);
    }

    /**
     * 使用自定义提供的文件content-type将请求文件添加到请求
     *
     * @param key
     * @param file
     * @param contentType    文件的内容类型，例如:application/json
     * @throws FileNotFoundException
     */
    public void put(String key, File file, String contentType) throws FileNotFoundException {
        put(key, file, contentType, null);
    }

    /**
     * 使用自定义提供的文件content-type和文件名将请求添加到请求中
     *
     * @param key
     * @param file
     * @param contentType    文件的内容类型，例如:application/json
     * @param customFileName 使用文件名而不是真正的文件名
     * @throws FileNotFoundException
     */
    public void put(String key, File file, String contentType, String customFileName) throws FileNotFoundException {
        if (file == null || !file.exists()) {
            throw new FileNotFoundException();
        }
        if (key != null) {
            fileParams.put(key, new FileWrapper(file, contentType, customFileName));
        }
    }

    /**
     * 向请求添加输入流。
     *
     * @param key
     * @param stream
     */
    public void put(String key, InputStream stream) {
        put(key, stream, null);
    }

    /**
     * 向请求添加输入流。
     *
     * @param key
     * @param stream
     * @param name   流的名称
     */
    public void put(String key, InputStream stream, String name) {
        put(key, stream, name, null);
    }

    /**
     * 向请求添加输入流。
     *
     * @param key
     * @param stream
     * @param name   流的名称
     * @param contentType    文件的内容类型，例如:application/json
     */
    public void put(String key, InputStream stream, String name, String contentType) {
        put(key, stream, name, contentType, autoCloseInputStreams);
    }

    /**
     * 向请求添加输入流。
     *
     * @param key
     * @param stream
     * @param name   流的名称
     * @param contentType    文件的内容类型，例如:application/json
     * @param autoClose   上传成功后自动关闭输入流
     */
    public void put(String key, InputStream stream, String name, String contentType, boolean autoClose) {
        if (key != null && stream != null) {
            streamParams.put(key, StreamWrapper.newInstance(stream, name, contentType, autoClose));
        }
    }

    /**
     * 添加具有非字符串值的参数(e.g. Map, List, Set).
     *
     * @param key
     * @param value 新参数的非字符串值对象。
     */
    public void put(String key, Object value) {
        if (key != null && value != null) {
            urlParamsWithObjects.put(key, value);
        }
    }

    /**
     * 向请求添加一个int值。
     *
     * @param key
     * @param value
     */
    public void put(String key, int value) {
        if (key != null) {
            urlParams.put(key, String.valueOf(value));
        }
    }

    /**
     * 向请求添加一个long值。
     *
     * @param key
     * @param value
     */
    public void put(String key, long value) {
        if (key != null) {
            urlParams.put(key, String.valueOf(value));
        }
    }

    /**
     * 将字符串值添加到可以有多个值的参数中。
     *
     * @param key   the key name for the param, either existing or new.
     * @param value the value string for the new param.
     */
	public void add(String key, String value) {
        if (key != null && value != null) {
            Object params = urlParamsWithObjects.get(key);
            if (params == null) {
                // Backward compatible, which will result in "k=v1&k=v2&k=v3"
                params = new HashSet<String>();
                this.put(key, params);
            }
            if (params instanceof List) {
                ((List<Object>) params).add(value);
            } else if (params instanceof Set) {
                ((Set<Object>) params).add(value);
            }
        }
    }

    /**
     * 从请求中删除参数。
     *
     * @param key
     */
    public void remove(String key) {
        urlParams.remove(key);
        streamParams.remove(key);
        fileParams.remove(key);
        urlParamsWithObjects.remove(key);
        fileArrayParams.remove(key);
    }

    /**
     * 检查参数是否定义
     *
     * @param key the key name for the parameter to check existence.
     * @return Boolean
     */
    public boolean has(String key) {
        return urlParams.get(key) != null ||
                streamParams.get(key) != null ||
                fileParams.get(key) != null ||
                urlParamsWithObjects.get(key) != null ||
                fileArrayParams.get(key) != null;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (ConcurrentHashMap.Entry<String, String> entry : urlParams.entrySet()) {
            if (result.length() > 0)
                result.append("&");

            result.append(entry.getKey());
            result.append("=");
            result.append(entry.getValue());
        }

        for (ConcurrentHashMap.Entry<String, StreamWrapper> entry : streamParams.entrySet()) {
            if (result.length() > 0)
                result.append("&");

            result.append(entry.getKey());
            result.append("=");
            result.append("STREAM");
        }

        for (ConcurrentHashMap.Entry<String, FileWrapper> entry : fileParams.entrySet()) {
            if (result.length() > 0)
                result.append("&");

            result.append(entry.getKey());
            result.append("=");
            result.append("FILE");
        }

        for (ConcurrentHashMap.Entry<String, List<FileWrapper>> entry : fileArrayParams.entrySet()) {
            if (result.length() > 0)
                result.append("&");

            result.append(entry.getKey());
            result.append("=");
            result.append("FILES(SIZE=").append(entry.getValue().size()).append(")");
        }

        List<BasicNameValuePair> params = getParamsList(null, urlParamsWithObjects);
        for (BasicNameValuePair kv : params) {
            if (result.length() > 0)
                result.append("&");

            result.append(kv.getName());
            result.append("=");
            result.append(kv.getValue());
        }

        return result.toString();
    }

    /**
     * 设置Http实体是可重复的
     * @param flag
     */
    public void setHttpEntityIsRepeatable(boolean flag) {
        this.isRepeatable = flag;
    }

    /**
     * 设置使用JsonStreamer
     * @param flag
     */
    public void setUseJsonStreamer(boolean flag) {
        this.useJsonStreamer = flag;
    }

    /**
     * 通过流传送上传JSON对象时设置一个附加字段，以保存上载有效载荷所需的时间（以毫秒为单位）。<br> 
     * 默认情况下，此字段设置为“_elapsed”。
     * <p>&nbsp;</p>
     * 要禁用此功能，请将此方法调用为null作为字段值。
     *
     * @param value 字段名称，以添加已用时间，或为空禁用
     */
    public void setElapsedFieldInJsonStreamer(String value) {
        this.elapsedFieldInJsonStreamer = value;
    }

    /**
     * 设置全局标志，用于确定在成功上传时是否自动关闭输入流。
     *
     * @param flag 是否自动关闭输入流
     */
    public void setAutoCloseInputStreams(boolean flag) {
        autoCloseInputStreams = flag;
    }

    /**
     * 返回包含所有请求参数的HttpEntity。
     *
     * @param progressHandler HttpResponseHandler报告实体提交进度
     * @return HttpEntity 结果HttpEntity与{@link org.apache.http.client.methods.HttpEntityEnclosingRequestBase}一起被包括在内
     * @throws IOException 如果其中一个流不能被读取
     */
    public HttpEntity getEntity(ResponseHandlerInterface progressHandler) throws IOException {
        if (useJsonStreamer) {
            return createJsonStreamerEntity(progressHandler);
        } else if (!forceMultipartEntity && streamParams.isEmpty() && fileParams.isEmpty() && fileArrayParams.isEmpty()) {
            return createFormEntity();
        } else {
            return createMultipartEntity(progressHandler);
        }
    }

    /**
     * 创建JsonStream实体
     * 
     * @param progressHandler
     * @return
     * @throws IOException
     */
    private HttpEntity createJsonStreamerEntity(ResponseHandlerInterface progressHandler) throws IOException {
        JsonStreamerEntity entity = new JsonStreamerEntity(
                progressHandler,
                !fileParams.isEmpty() || !streamParams.isEmpty(),
                elapsedFieldInJsonStreamer);

        // Add string params
        for (ConcurrentHashMap.Entry<String, String> entry : urlParams.entrySet()) {
            entity.addPart(entry.getKey(), entry.getValue());
        }

        // Add non-string params
        for (ConcurrentHashMap.Entry<String, Object> entry : urlParamsWithObjects.entrySet()) {
            entity.addPart(entry.getKey(), entry.getValue());
        }

        // Add file params
        for (ConcurrentHashMap.Entry<String, FileWrapper> entry : fileParams.entrySet()) {
            entity.addPart(entry.getKey(), entry.getValue());
        }

        // Add stream params
        for (ConcurrentHashMap.Entry<String, StreamWrapper> entry : streamParams.entrySet()) {
            StreamWrapper stream = entry.getValue();
            if (stream.inputStream != null) {
                entity.addPart(entry.getKey(),
                        StreamWrapper.newInstance(
                                stream.inputStream,
                                stream.name,
                                stream.contentType,
                                stream.autoClose)
                );
            }
        }

        return entity;
    }

    /**
     * 创建表单实体
     * 
     * @return
     */
    private HttpEntity createFormEntity() {
        try {
            return new UrlEncodedFormEntity(getParamsList(), contentEncoding);
        } catch (UnsupportedEncodingException e) {
            AsyncHttpClient.log.e(LOG_TAG, "createFormEntity failed", e);
            return null; // Can happen, if the 'contentEncoding' won't be HTTP.UTF_8
        }
    }

    /**
     * 创建多部分实体
     * 
     * @param progressHandler
     * @return
     * @throws IOException
     */
    private HttpEntity createMultipartEntity(ResponseHandlerInterface progressHandler) throws IOException {
        SimpleMultipartEntity entity = new SimpleMultipartEntity(progressHandler);
        entity.setIsRepeatable(isRepeatable);

        // Add string params
        for (ConcurrentHashMap.Entry<String, String> entry : urlParams.entrySet()) {
            entity.addPartWithCharset(entry.getKey(), entry.getValue(), contentEncoding);
        }

        // Add non-string params
        List<BasicNameValuePair> params = getParamsList(null, urlParamsWithObjects);
        for (BasicNameValuePair kv : params) {
            entity.addPartWithCharset(kv.getName(), kv.getValue(), contentEncoding);
        }

        // Add stream params
        for (ConcurrentHashMap.Entry<String, StreamWrapper> entry : streamParams.entrySet()) {
            StreamWrapper stream = entry.getValue();
            if (stream.inputStream != null) {
                entity.addPart(entry.getKey(), stream.name, stream.inputStream,
                        stream.contentType);
            }
        }

        // Add file params
        for (ConcurrentHashMap.Entry<String, FileWrapper> entry : fileParams.entrySet()) {
            FileWrapper fileWrapper = entry.getValue();
            entity.addPart(entry.getKey(), fileWrapper.file, fileWrapper.contentType, fileWrapper.customFileName);
        }

        // Add file collection
        for (ConcurrentHashMap.Entry<String, List<FileWrapper>> entry : fileArrayParams.entrySet()) {
            List<FileWrapper> fileWrapper = entry.getValue();
            for (FileWrapper fw : fileWrapper) {
                entity.addPart(entry.getKey(), fw.file, fw.contentType, fw.customFileName);
            }
        }

        return entity;
    }

    /**
     * 获取参数列表
     * 
     * @return
     */
    protected List<BasicNameValuePair> getParamsList() {
        List<BasicNameValuePair> lparams = new LinkedList<BasicNameValuePair>();

        for (ConcurrentHashMap.Entry<String, String> entry : urlParams.entrySet()) {
            lparams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }

        lparams.addAll(getParamsList(null, urlParamsWithObjects));

        return lparams;
    }

    /**
     * 获取参数列表
     * 
     * @param key
     * @param value
     * @return
     */
    private List<BasicNameValuePair> getParamsList(String key, Object value) {
        List<BasicNameValuePair> params = new LinkedList<BasicNameValuePair>();
        if (value instanceof Map) {
            Map map = (Map) value;
            List list = new ArrayList<Object>(map.keySet());
            // Ensure consistent ordering in query string
            if (list.size() > 0 && list.get(0) instanceof Comparable) {
                Collections.sort(list);
            }
            for (Object nestedKey : list) {
                if (nestedKey instanceof String) {
                    Object nestedValue = map.get(nestedKey);
                    if (nestedValue != null) {
                        params.addAll(getParamsList(key == null ? (String) nestedKey : String.format(Locale.US, "%s[%s]", key, nestedKey),
                                nestedValue));
                    }
                }
            }
        } else if (value instanceof List) {
            List list = (List) value;
            int listSize = list.size();
            for (int nestedValueIndex = 0; nestedValueIndex < listSize; nestedValueIndex++) {
                params.addAll(getParamsList(String.format(Locale.US, "%s[%d]", key, nestedValueIndex), list.get(nestedValueIndex)));
            }
        } else if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            int arrayLength = array.length;
            for (int nestedValueIndex = 0; nestedValueIndex < arrayLength; nestedValueIndex++) {
                params.addAll(getParamsList(String.format(Locale.US, "%s[%d]", key, nestedValueIndex), array[nestedValueIndex]));
            }
        } else if (value instanceof Set) {
            Set set = (Set) value;
            for (Object nestedValue : set) {
                params.addAll(getParamsList(key, nestedValue));
            }
        } else {
            params.add(new BasicNameValuePair(key, value.toString()));
        }
        return params;
    }

    /**
     * 获取参数字符串
     * 
     * @return
     */
    protected String getParamString() {
        return URLEncodedUtils.format(getParamsList(), contentEncoding);
    }

    /**
     * 文件包装
     * 
     * @author lijian
     * @date 2017-7-27 下午10:04:49
     */
    public static class FileWrapper implements Serializable {
		private static final long serialVersionUID = 1L;
		
		public final File file;
        public final String contentType;
        public final String customFileName;// 自定义文件名

        public FileWrapper(File file, String contentType, String customFileName) {
            this.file = file;
            this.contentType = contentType;
            this.customFileName = customFileName;
        }
    }

	/**
	 * 数据流包装
	 * 
	 * @author lijian
	 * @date 2017-7-27 下午9:59:54
	 */
	public static class StreamWrapper {
        public final InputStream inputStream;
        public final String name;
        public final String contentType;
        public final boolean autoClose;

        public StreamWrapper(InputStream inputStream, String name, String contentType, boolean autoClose) {
            this.inputStream = inputStream;
            this.name = name;
            this.contentType = contentType;
            this.autoClose = autoClose;
        }

        static StreamWrapper newInstance(InputStream inputStream, String name, String contentType, boolean autoClose) {
            return new StreamWrapper(
                    inputStream,
                    name,
                    contentType == null ? APPLICATION_OCTET_STREAM : contentType,
                    autoClose);
        }
    }
}
