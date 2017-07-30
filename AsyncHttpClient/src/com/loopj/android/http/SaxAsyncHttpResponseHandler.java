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
import org.apache.http.HttpEntity;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * 提供使用AsyncHttpResponseHandler反序列化SAX响应的接口。<br> 
 * 可以这样使用
 * <p>&nbsp;</p>
 * <pre>
 *     AsyncHttpClient ahc = new AsyncHttpClient();
 *     FontHandler handlerInstance = ... ; // init handler instance
 *     ahc.post("https://server.tld/api/call", new SaxAsyncHttpResponseHandler{@literal <}FontHandler{@literal >}(handlerInstance){
 *         &#064;Override
 *         public void onSuccess(int statusCode, Header[] headers, FontHandler t) {
 *              // Request got HTTP success statusCode
 *         }
 *         &#064;Override
 *         public void onFailure(int statusCode, Header[] headers, FontHandler t){
 *              // Request got HTTP fail statusCode
 *         }
 *     });
 * </pre>
 *
 * @param <T> Handler extending {@link org.xml.sax.helpers.DefaultHandler}
 * @see org.xml.sax.helpers.DefaultHandler
 * @see com.loopj.android.http.AsyncHttpResponseHandler
 */
public abstract class SaxAsyncHttpResponseHandler<T extends DefaultHandler> extends AsyncHttpResponseHandler {

    /**
     * 通用类型的处理程序
     */
    private T handler = null;
    private final static String LOG_TAG = "SaxAsyncHttpRH";

    /**
     * 使用给定的处理程序实例构造新的SaxAsyncHttpResponseHandler
     *
     * @param t Handler扩展DefaultHandler的实例
     * @see org.xml.sax.helpers.DefaultHandler
     */
    public SaxAsyncHttpResponseHandler(T t) {
        super();
        if (t == null) {
            throw new Error("null instance of <T extends DefaultHandler> passed to constructor");
        }
        this.handler = t;
    }

    /**
     * 将响应解构为给定的内容处理程序
     *
     * @param entity 返回HttpEntity
     * @return 解构响应
     * @throws java.io.IOException 如果从流中组装SAX响应有问题
     * @see org.apache.http.HttpEntity
     */
    @Override
    protected byte[] getResponseData(HttpEntity entity) throws IOException {
        if (entity != null) {
            InputStream instream = entity.getContent();
            InputStreamReader inputStreamReader = null;
            if (instream != null) {
                try {
                    SAXParserFactory sfactory = SAXParserFactory.newInstance();
                    SAXParser sparser = sfactory.newSAXParser();
                    XMLReader rssReader = sparser.getXMLReader();
                    rssReader.setContentHandler(handler);
                    inputStreamReader = new InputStreamReader(instream, getCharset());
                    rssReader.parse(new InputSource(inputStreamReader));
                } catch (SAXException e) {
                    AsyncHttpClient.log.e(LOG_TAG, "getResponseData exception", e);
                } catch (ParserConfigurationException e) {
                    AsyncHttpClient.log.e(LOG_TAG, "getResponseData exception", e);
                } finally {
                    AsyncHttpClient.silentCloseInputStream(instream);
                    if (inputStreamReader != null) {
                        try {
                            inputStreamReader.close();
                        } catch (IOException e) { /*ignore*/ }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 这个AsyncHttpResponseHandler的默认onSuccess方法来覆盖
     *
     * @param statusCode 返回HTTP状态码
     * @param headers    返回HTTP请求头
     * @param t          Handler扩展DefaultHandler的实例
     */
    public abstract void onSuccess(int statusCode, Header[] headers, T t);

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
        onSuccess(statusCode, headers, handler);
    }

    /**
     * 这个AsyncHttpResponseHandler的默认onFailure方法来覆盖
     *
     * @param statusCode 返回HTTP状态码
     * @param headers    返回HTTP请求头
     * @param t          Handler扩展DefaultHandler的实例
     */
    public abstract void onFailure(int statusCode, Header[] headers, T t);

    @Override
    public void onFailure(int statusCode, Header[] headers,
                          byte[] responseBody, Throwable error) {
        onFailure(statusCode, headers, handler);
    }
}
