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
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个实现Apache HttpClient {@link CookieStore}接口的持久性cookie存储。 
 * Cookie被存储，并将在应用程序会话之间持久存储在用户设备上，因为它们被序列化并存储在{@link SharedPreferences}中。
 * 
 * <p>&nbsp;</p> 
 * 该类的实例被设计为与{@link AsyncHttpClient#setCookieStore}一起使用，
 * 但是如果您愿意，也可以使用常规的旧的apache HttpClient/HttpContext
 */
public class PersistentCookieStore implements CookieStore {
    private static final String LOG_TAG = "PersistentCookieStore";
    private static final String COOKIE_PREFS = "CookiePrefsFile";
    private static final String COOKIE_NAME_STORE = "names";
    /** cookie的前缀 */
    private static final String COOKIE_NAME_PREFIX = "cookie_";
    /** 省略非持久性cookie */
    private boolean omitNonPersistentCookies = false;

    private final ConcurrentHashMap<String, Cookie> cookies;
    private final SharedPreferences cookiePrefs;

    /**
     * 构造一个持久的cookie存储。
     *
     * @param context 将cookie存储附加到的上下文
     */
    public PersistentCookieStore(Context context) {
        cookiePrefs = context.getSharedPreferences(COOKIE_PREFS, 0);
        cookies = new ConcurrentHashMap<String, Cookie>();

        // 将任何以前存储的Cookie加载到store
        String storedCookieNames = cookiePrefs.getString(COOKIE_NAME_STORE, null);
        if (storedCookieNames != null) {
            String[] cookieNames = TextUtils.split(storedCookieNames, ",");
            for (String name : cookieNames) {
                String encodedCookie = cookiePrefs.getString(COOKIE_NAME_PREFIX + name, null);
                if (encodedCookie != null) {
                    Cookie decodedCookie = decodeCookie(encodedCookie);
                    if (decodedCookie != null) {
                        cookies.put(name, decodedCookie);
                    }
                }
            }

            // 清除过期的Cookie
            clearExpired(new Date());
        }
    }

    @Override
    public void addCookie(Cookie cookie) {
        if (omitNonPersistentCookies && !cookie.isPersistent())
            return;
        String name = cookie.getName() + cookie.getDomain();

        // 将cookie保存到本地存储，如果过期则删除
        if (!cookie.isExpired(new Date())) {
            cookies.put(name, cookie);
        } else {
            cookies.remove(name);
        }

        // 将Cookie保存到持久存储中
        SharedPreferences.Editor prefsWriter = cookiePrefs.edit();
        prefsWriter.putString(COOKIE_NAME_STORE, TextUtils.join(",", cookies.keySet()));
        prefsWriter.putString(COOKIE_NAME_PREFIX + name, encodeCookie(new SerializableCookie(cookie)));
        prefsWriter.commit();
    }

    @Override
    public void clear() {
        // 从持久性store清除Cookie
        SharedPreferences.Editor prefsWriter = cookiePrefs.edit();
        for (String name : cookies.keySet()) {
            prefsWriter.remove(COOKIE_NAME_PREFIX + name);
        }
        prefsWriter.remove(COOKIE_NAME_STORE);
        prefsWriter.commit();

        // 从本地存储清除Cookies
        cookies.clear();
    }

    @Override
    public boolean clearExpired(Date date) {
        boolean clearedAny = false;
        SharedPreferences.Editor prefsWriter = cookiePrefs.edit();

        for (ConcurrentHashMap.Entry<String, Cookie> entry : cookies.entrySet()) {
            String name = entry.getKey();
            Cookie cookie = entry.getValue();
            if (cookie.isExpired(date)) {
                // 从本地存储清除Cookies
                cookies.remove(name);

                // 从持久性store清除Cookie
                prefsWriter.remove(COOKIE_NAME_PREFIX + name);

                // 我们已经清理了至少一个
                clearedAny = true;
            }
        }

        // 更新持久存储中的名称
        if (clearedAny) {
            prefsWriter.putString(COOKIE_NAME_STORE, TextUtils.join(",", cookies.keySet()));
        }
        prefsWriter.commit();

        return clearedAny;
    }

    @Override
    public List<Cookie> getCookies() {
        return new ArrayList<Cookie>(cookies.values());
    }

    /**
     * 将使PersistentCookieStore实例忽略Cookie，这是通过签名(`Cookie.isPersistent`)不持久的Cookie
     *
     * @param omitNonPersistentCookies true:非持久性cookies应该被省略
     */
    public void setOmitNonPersistentCookies(boolean omitNonPersistentCookies) {
        this.omitNonPersistentCookies = omitNonPersistentCookies;
    }

    /**
     * 非标准帮助方法，删除cookie
     *
     * @param cookie 要删除的cookie
     */
    public void deleteCookie(Cookie cookie) {
        String name = cookie.getName() + cookie.getDomain();
        cookies.remove(name);
        SharedPreferences.Editor prefsWriter = cookiePrefs.edit();
        prefsWriter.remove(COOKIE_NAME_PREFIX + name);
        prefsWriter.commit();
    }

    /**
     * 将Cookie对象序列化为String
     *
     * @param cookie 要编码的cookie可以为null
     * @return cookie 编码为String
     */
    protected String encodeCookie(SerializableCookie cookie) {
        if (cookie == null)
            return null;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(os);
            outputStream.writeObject(cookie);
        } catch (IOException e) {
            AsyncHttpClient.log.d(LOG_TAG, "IOException in encodeCookie", e);
            return null;
        }

        return byteArrayToHexString(os.toByteArray());
    }

    /**
     * 从cookie字符串返回cookie解码
     *
     * @param cookieString 从http请求返回的cookie的字符串
     * @return 解码的cookie，如果发生异常则为null
     */
    protected Cookie decodeCookie(String cookieString) {
        byte[] bytes = hexStringToByteArray(cookieString);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        Cookie cookie = null;
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            cookie = ((SerializableCookie) objectInputStream.readObject()).getCookie();
        } catch (IOException e) {
            AsyncHttpClient.log.d(LOG_TAG, "IOException in decodeCookie", e);
        } catch (ClassNotFoundException e) {
            AsyncHttpClient.log.d(LOG_TAG, "ClassNotFoundException in decodeCookie", e);
        }

        return cookie;
    }

    /**
     * 使用一些超基本字节数组&lt;-&gt;十六进制转换，所以我们不必依赖任何大的Base64库。 如果你喜欢可以覆盖！
     *
     * @param bytes 要转换的字节数组
     * @return 字符串包含十六进制值
     */
    protected String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte element : bytes) {
            int v = element & 0xff;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    /**
     * 将十六进制值从字符串转换为字节数组
     *
     * @param hexString 十六进制编码值的字符串
     * @return 解码字节数组
     */
    protected byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }
}