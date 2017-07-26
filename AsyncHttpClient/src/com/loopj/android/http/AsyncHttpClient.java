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
import android.os.Looper;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.SyncBasicHttpContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * 异步HttpClient<br>
 * 在你的Android应用程序中可以使用AsyncHttpClient创建异步的GET, POST, PUT and DELETE HTTP请求。<br>
 * 你还可以通过使用{@link RequestParams}实例，给你的请求添加额外的参数。<br>
 * 你可以通过匿名的形式重写{@link ResponseHandlerInterface}实例以处理responses应答。
 * <p>
 * &nbsp;
 * </p>
 * For example:
 * <p>
 * &nbsp;
 * </p>
 * 
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * client.get(&quot;https://www.baidu.com&quot;, new AsyncHttpResponseHandler() {
 * 	&#064;Override
 * 	public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
 * 		System.out.println(response);
 * 	}
 * 
 * 	&#064;Override
 * 	public void onFailure(int statusCode, Header[] headers,
 * 			byte[] responseBody, Throwable error) {
 * 		error.printStackTrace(System.out);
 * 	}
 * });
 * </pre>
 * 
 * @see com.loopj.android.http.AsyncHttpResponseHandler
 * @see com.loopj.android.http.ResponseHandlerInterface
 * @see com.loopj.android.http.RequestParams
 */
public class AsyncHttpClient {
	public static final String LOG_TAG = "AsyncHttpClient";

	/** 请求头相关 */
	public static final String HEADER_CONTENT_TYPE = "Content-Type";
	public static final String HEADER_CONTENT_RANGE = "Content-Range";
	public static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
	public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
	public static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
	public static final String ENCODING_GZIP = "gzip";

	/** 默认属性设置 */
	public static final int DEFAULT_MAX_CONNECTIONS = 10;// 最大连接数
	public static final int DEFAULT_SOCKET_TIMEOUT = 10 * 1000;// socket超时
	public static final int DEFAULT_MAX_RETRIES = 5;// 重试次数
	public static final int DEFAULT_RETRY_SLEEP_TIME_MILLIS = 1500;// 重试睡眠时间
	public static final int DEFAULT_SOCKET_BUFFER_SIZE = 8192;// socket缓存区大小

	/** 最大连接数 */
	private int maxConnections = DEFAULT_MAX_CONNECTIONS;
	/** 连接超时 */
	private int connectTimeout = DEFAULT_SOCKET_TIMEOUT;
	/** 请求超时 */
	private int responseTimeout = DEFAULT_SOCKET_TIMEOUT;

	/** 网络请求HttpClient实例 */
	private final DefaultHttpClient httpClient;
	/** 网络请求HttpContext实例 */
	private final HttpContext httpContext;
	/** 线程池 */
	private ExecutorService threadPool;
	/** 请求参数集合-请求头 */
	private final Map<Context, List<RequestHandle>> requestMap;
	/** 请求头集合 */
	private final Map<String, String> clientHeaderMap;
	/** Url编码是否激活 */
	private boolean isUrlEncodingEnabled = true;

	/** 日志 */
	public static LogInterface log = new LogHandler();

	/**
	 * 创建一个AsyncHttpClient将使用默认参数（默认http使用80端口，https使用443端口）
	 */
	public AsyncHttpClient() {
		this(false, 80, 443);
	}

	/**
	 * 创建一个AsyncHttpClient.
	 * 
	 * @param httpPort
	 *            设置一个http请求端口
	 */
	public AsyncHttpClient(int httpPort) {
		this(false, httpPort, 443);
	}

	/**
	 * 创建一个AsyncHttpClient.
	 * 
	 * @param httpPort
	 *            设置一个http请求端口
	 * @param httpsPort
	 *            设置一个https请求端口
	 */
	public AsyncHttpClient(int httpPort, int httpsPort) {
		this(false, httpPort, httpsPort);
	}

	/**
	 * 使用给定的参数创建一个AsyncHttpClient
	 * 
	 * @param fixNoHttpResponseException
	 *            是否通过省略SSL验证来解决问题
	 * @param httpPort
	 *            要使用的HTTP端口必须大于0。
	 * @param httpsPort
	 *            要使用的HTTPS端口必须大于0
	 */
	public AsyncHttpClient(boolean fixNoHttpResponseException, int httpPort,
			int httpsPort) {
		this(getDefaultSchemeRegistry(fixNoHttpResponseException, httpPort,
				httpsPort));
	}

	/**
	 * 返回一个默认SchemeRegistry实例
	 * 
	 * @param fixNoHttpResponseException
	 *            是否通过省略SSL验证来解决问题
	 * @param httpPort
	 *            要使用的HTTP端口必须大于0。
	 * @param httpsPort
	 *            要使用的HTTPS端口必须大于0
	 */
	private static SchemeRegistry getDefaultSchemeRegistry(
			boolean fixNoHttpResponseException, int httpPort, int httpsPort) {
		if (fixNoHttpResponseException) {
			log.d(LOG_TAG, "当心！使用该修补程序是不安全的，因为它不验证SSL证书。");
		}

		if (httpPort < 1) {
			httpPort = 80;
			log.d(LOG_TAG, "指定的HTTP端口号无效，默认为80");
		}

		if (httpsPort < 1) {
			httpsPort = 443;
			log.d(LOG_TAG, "指定的HTTPS端口号无效，默认为443");
		}

		// 修复SSL漏洞API < ICS
		// See https://code.google.com/p/android/issues/detail?id=13117
		SSLSocketFactory sslSocketFactory;
		if (fixNoHttpResponseException) {
			sslSocketFactory = MySSLSocketFactory.getFixedSocketFactory();
		} else {
			sslSocketFactory = SSLSocketFactory.getSocketFactory();
		}

		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory
				.getSocketFactory(), httpPort));
		schemeRegistry
				.register(new Scheme("https", sslSocketFactory, httpsPort));

		return schemeRegistry;
	}

	/**
	 * 使用SchemeRegistry创建 一个AsyncHttpClient
	 * 
	 * @param schemeRegistry
	 *            SchemeRegistry to be used
	 */
	public AsyncHttpClient(SchemeRegistry schemeRegistry) {
		// 创建一个“基本HTTP参数”，并设置初始化参数
		BasicHttpParams httpParams = new BasicHttpParams();

		ConnManagerParams.setTimeout(httpParams, connectTimeout);
		ConnManagerParams.setMaxConnectionsPerRoute(httpParams,
				new ConnPerRouteBean(maxConnections));
		ConnManagerParams.setMaxTotalConnections(httpParams,
				DEFAULT_MAX_CONNECTIONS);

		HttpConnectionParams.setSoTimeout(httpParams, responseTimeout);
		HttpConnectionParams.setConnectionTimeout(httpParams, connectTimeout);
		HttpConnectionParams.setTcpNoDelay(httpParams, true);
		HttpConnectionParams.setSocketBufferSize(httpParams,
				DEFAULT_SOCKET_BUFFER_SIZE);

		HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);

		// 客户端连接管理器
		ClientConnectionManager cm = createConnectionManager(schemeRegistry,
				httpParams);
		Utils.asserts(
				cm != null,
				"Custom implementation of #createConnectionManager(SchemeRegistry, BasicHttpParams) returned null");

		// 线程池
		threadPool = getDefaultThreadPool();
		// 请求参数集合-请求头 
		requestMap = Collections
				.synchronizedMap(new WeakHashMap<Context, List<RequestHandle>>());
		clientHeaderMap = new HashMap<String, String>();

		httpContext = new SyncBasicHttpContext(new BasicHttpContext());
		httpClient = new DefaultHttpClient(cm, httpParams);
		// 添加请求拦截器(对请求头进行处理)
		httpClient.addRequestInterceptor(new HttpRequestInterceptor() {
			@Override
			public void process(HttpRequest request, HttpContext context) {
				if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
					request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
				}
				for (String header : clientHeaderMap.keySet()) {
					if (request.containsHeader(header)) {
						Header overwritten = request.getFirstHeader(header);
						log.d(LOG_TAG,
								String.format(
										"Headers were overwritten! (%s | %s) overwrites (%s | %s)",
										header, clientHeaderMap.get(header),
										overwritten.getName(),
										overwritten.getValue()));

						// remove the overwritten header
						request.removeHeader(overwritten);
					}
					request.addHeader(header, clientHeaderMap.get(header));
				}
			}
		});

		// 添加响应拦截器（对响应进行gzip解码）
		httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
			@Override
			public void process(HttpResponse response, HttpContext context) {
				final HttpEntity entity = response.getEntity();
				if (entity == null) {
					return;
				}
				final Header encoding = entity.getContentEncoding();
				if (encoding != null) {
					for (HeaderElement element : encoding.getElements()) {
						if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
							// gzip解码
							response.setEntity(new InflatingEntity(entity));
							break;
						}
					}
				}
			}
		});

		// 添加请求拦截器
		httpClient.addRequestInterceptor(new HttpRequestInterceptor() {
			@Override
			public void process(final HttpRequest request,
					final HttpContext context) throws HttpException,
					IOException {
				// 认证状态
				AuthState authState = (AuthState) context
						.getAttribute(ClientContext.TARGET_AUTH_STATE);
				// 证书提供者
				CredentialsProvider credsProvider = (CredentialsProvider) context
						.getAttribute(ClientContext.CREDS_PROVIDER);
				// 目标主机
				HttpHost targetHost = (HttpHost) context
						.getAttribute(ExecutionContext.HTTP_TARGET_HOST);

				// 如果认证Scheme为null，则重新获取认证得到证书
				if (authState.getAuthScheme() == null) {
					AuthScope authScope = new AuthScope(targetHost
							.getHostName(), targetHost.getPort());
					Credentials creds = credsProvider.getCredentials(authScope);
					if (creds != null) {
						authState.setAuthScheme(new BasicScheme());
						authState.setCredentials(creds);
					}
				}
			}
		}, 0);

		// 设置Http请求重试处理程序
		httpClient.setHttpRequestRetryHandler(new RetryHandler(
				DEFAULT_MAX_RETRIES, DEFAULT_RETRY_SLEEP_TIME_MILLIS));
	}

	/**
	 * 允许重试异常类
	 * 
	 * @param cls
	 */
	public static void allowRetryExceptionClass(Class<?> cls) {
		if (cls != null) {
			RetryHandler.addClassToWhitelist(cls);
		}
	}

	/**
	 * 黑名单（不允许重试的异常类）
	 * 
	 * @param cls
	 */
	public static void blackRetryExceptionClass(Class<?> cls) {
		if (cls != null) {
			RetryHandler.addClassToBlacklist(cls);
		}
	}

	/**
	 * 获取底层的HttpClient实例<br>
	 * 这通过访问客户端的ConnectionManager，HttpParams和SchemeRegistry来为请求设置其他细粒度设置非常有用。
	 * 
	 * @return 底层HttpClient实例
	 */
	public HttpClient getHttpClient() {
		return this.httpClient;
	}

	/**
	 * 获取底层的HttpContext实例<br>
	 * 这通过访问上下文的属性（如CookieStore）来获取和设置请求的细粒度设置非常有用。
	 * 
	 * @return 底层HttpContext实例
	 */
	public HttpContext getHttpContext() {
		return this.httpContext;
	}

	/**
	 * 将在LogInterface底层实例上设置记录启用标志。 默认设置是启用日志记录。
	 * 
	 * @param loggingEnabled
	 *            是否应启用日志记录(true:开启；false:关闭)
	 */
	public void setLoggingEnabled(boolean loggingEnabled) {
		log.setLoggingEnabled(loggingEnabled);
	}

	/**
	 * 从底层LogInterface实例返回记录启用标志<br>
	 * 默认设置是启用日志记录。
	 * 
	 * @return boolean 日志当前状态（true:开启：false:关闭）
	 */
	public boolean isLoggingEnabled() {
		return log.isLoggingEnabled();
	}

	/**
	 * 设置日志等级<br>
	 * 默认设置为VERBOSE日志级别。
	 * 
	 * @param logLevel
	 *            日志级别，可查考{@link android.util.Log}
	 */
	public void setLoggingLevel(int logLevel) {
		log.setLoggingLevel(logLevel);
	}

	/**
	 * 当前日志级别<br>
	 * 默认设置为VERBOSE日志级别。
	 * 
	 * @return
	 */
	public int getLoggingLevel() {
		return log.getLoggingLevel();
	}

	/**
	 * 将返回AsyncHttpClient实例中使用的当前LogInterface
	 * 
	 * @return AsyncHttpClient实例当前使用的LogInterface
	 */
	public LogInterface getLogInterface() {
		return log;
	}

	/**
	 * 设置默认LogInterface（类似于std Android Log util class）实例，用于AsyncHttpClient实例
	 * 
	 * @param logInterfaceInstance
	 *            LogInterface实例，如果为null，则不做任何操作
	 */
	public void setLogInterface(LogInterface logInterfaceInstance) {
		if (logInterfaceInstance != null) {
			log = logInterfaceInstance;
		}
	}

	/**
	 * 设置可选的CookieStore以在请求时使用
	 * 
	 * @param cookieStore
	 *            要使用的CookieStore实现，通常是{@link PersistentCookieStore}的一个实例
	 */
	public void setCookieStore(CookieStore cookieStore) {
		httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
	}

	/**
	 * 覆盖在queuing/pooling请求时使用的线程池实现<br>
	 * 默认情况下，使用Executors.newCachedThreadPool()
	 * 
	 * @param threadPool
	 *            用于queuing/pooling请求的{@link ExecutorService}实例。
	 */
	public void setThreadPool(ExecutorService threadPool) {
		this.threadPool = threadPool;
	}

	/**
	 * 当前正在使用的ExecutorService<br>
	 * 默认使用的Executors.newCachedThreadPool()
	 * 
	 * @return
	 */
	public ExecutorService getThreadPool() {
		return threadPool;
	}

	/**
	 * 获取用于此HTTP客户端的默认线程池。
	 * 
	 * @return 要使用的默认线程池
	 */
	protected ExecutorService getDefaultThreadPool() {
		return Executors.newCachedThreadPool();
	}

	/**
	 * 提供此方法<br>
	 * 开发人员更容易提供自定义的ThreadSafeClientConnManager实现
	 * 
	 * @param schemeRegistry
	 *            SchemeRegistry通常由
	 *            {@link #getDefaultSchemeRegistry(boolean, int, int)}提供
	 * @param httpParams
	 *            BasicHttpParams
	 * @return ClientConnectionManager实例
	 */
	protected ClientConnectionManager createConnectionManager(
			SchemeRegistry schemeRegistry, BasicHttpParams httpParams) {
		return new ThreadSafeClientConnManager(httpParams, schemeRegistry);
	}

	/**
	 * 简单的接口方法，启用或禁用重定向<br>
	 * 如果在底层HttpClient上手动设置RedirectHandler，则此方法的效果将被取消。
	 * <p>
	 * &nbsp;
	 * </p>
	 * 默认设置是允许重定向。
	 * 
	 * @param enableRedirects
	 *            使重定向
	 * @param enableRelativeRedirects
	 *            使相对重定向
	 * @param enableCircularRedirects
	 *            使循环重定向
	 */
	public void setEnableRedirects(final boolean enableRedirects,
			final boolean enableRelativeRedirects,
			final boolean enableCircularRedirects) {
		httpClient.getParams()
				.setBooleanParameter(ClientPNames.REJECT_RELATIVE_REDIRECT,
						!enableRelativeRedirects);
		httpClient.getParams().setBooleanParameter(
				ClientPNames.ALLOW_CIRCULAR_REDIRECTS, enableCircularRedirects);
		httpClient.setRedirectHandler(new MyRedirectHandler(enableRedirects));
	}

	/**
	 * 循环重定向默认启用
	 * 
	 * @param enableRedirects
	 *            使重定向
	 * @param enableRelativeRedirects
	 *            使相对重定向
	 * @see #setEnableRedirects(boolean, boolean, boolean)
	 */
	public void setEnableRedirects(final boolean enableRedirects,
			final boolean enableRelativeRedirects) {
		setEnableRedirects(enableRedirects, enableRelativeRedirects, true);
	}

	/**
	 * @param enableRedirects
	 *            使重定向
	 * @see #setEnableRedirects(boolean, boolean, boolean)
	 */
	public void setEnableRedirects(final boolean enableRedirects) {
		setEnableRedirects(enableRedirects, enableRedirects, enableRedirects);
	}

	/**
	 * 允许您设置自定义的RedirectHandler实现，如果默认设置不适合你的需要
	 * 
	 * @param customRedirectHandler
	 *            RedirectHandler实例
	 * @see com.loopj.android.http.MyRedirectHandler
	 */
	public void setRedirectHandler(final RedirectHandler customRedirectHandler) {
		httpClient.setRedirectHandler(customRedirectHandler);
	}

	/**
	 * 给每个请求设置User-Agent header<br>
	 * 默认为"Android Asynchronous Http Client/VERSION (https://loopj.com/android-async-http/)"
	 * 
	 * @param userAgent
	 */
	public void setUserAgent(String userAgent) {
		HttpProtocolParams.setUserAgent(this.httpClient.getParams(), userAgent);
	}

	/**
	 * 返回当前并行连接数
	 * 
	 * @return 并行连接的最大限制，默认为10
	 */
	public int getMaxConnections() {
		return maxConnections;
	}

	/**
	 * 设置并行连接的最大值
	 * 
	 * @param maxConnections
	 *            最大并行连接数必须至少为1
	 */
	public void setMaxConnections(int maxConnections) {
		if (maxConnections < 1)
			maxConnections = DEFAULT_MAX_CONNECTIONS;
		this.maxConnections = maxConnections;
		final HttpParams httpParams = this.httpClient.getParams();
		ConnManagerParams.setMaxConnectionsPerRoute(httpParams,
				new ConnPerRouteBean(this.maxConnections));
	}

	/**
	 * 设置connection和socket超时<br>
	 * 默认情况下，两者都设置为10秒。
	 * 
	 * @param value
	 *            connect/socket超时（单位:毫秒），至少1秒
	 * @see #setConnectTimeout(int)
	 * @see #setResponseTimeout(int)
	 */
	public void setTimeout(int value) {
		value = value < 1000 ? DEFAULT_SOCKET_TIMEOUT : value;
		setConnectTimeout(value);
		setResponseTimeout(value);
	}

	/**
	 * 返回当前connection超时限制（毫秒）。 默认设置为10秒。
	 * 
	 * @return
	 */
	public int getConnectTimeout() {
		return connectTimeout;
	}

	/**
	 * 设置connection超时限制（毫秒）。 默认设置为10秒。
	 * 
	 * @param value
	 *            connect超时（单位:毫秒），至少1秒
	 */
	public void setConnectTimeout(int value) {
		connectTimeout = value < 1000 ? DEFAULT_SOCKET_TIMEOUT : value;
		final HttpParams httpParams = httpClient.getParams();
		ConnManagerParams.setTimeout(httpParams, connectTimeout);
		HttpConnectionParams.setConnectionTimeout(httpParams, connectTimeout);
	}

	/**
	 * 返回response超时<br>
	 * 默认为10秒。
	 * 
	 * @return
	 */
	public int getResponseTimeout() {
		return responseTimeout;
	}

	/**
	 * 设置response超时<br>
	 * 默认为10秒。
	 * 
	 * @param value
	 *            Response超时（单位:毫秒），至少1秒
	 */
	public void setResponseTimeout(int value) {
		responseTimeout = value < 1000 ? DEFAULT_SOCKET_TIMEOUT : value;
		final HttpParams httpParams = httpClient.getParams();
		HttpConnectionParams.setSoTimeout(httpParams, responseTimeout);
	}

	/**
	 * 通过hostname和port设置Proxy
	 * 
	 * @param hostname
	 *            the hostname (IP or DNS name)
	 * @param port
	 *            the port number. -1表示使用默认端口
	 */
	public void setProxy(String hostname, int port) {
		final HttpHost proxy = new HttpHost(hostname, port);
		final HttpParams httpParams = this.httpClient.getParams();
		httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
	}

	/**
	 * 通过hostname,port,username和password设置Proxy
	 * 
	 * @param hostname
	 *            the hostname (IP or DNS name)
	 * @param port
	 *            the port number. -1表示使用默认端口
	 * @param username
	 *            the 用户名
	 * @param password
	 *            the 密码
	 */
	public void setProxy(String hostname, int port, String username,
			String password) {
		httpClient.getCredentialsProvider().setCredentials(
				new AuthScope(hostname, port),
				new UsernamePasswordCredentials(username, password));
		final HttpHost proxy = new HttpHost(hostname, port);
		final HttpParams httpParams = this.httpClient.getParams();
		httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
	}

	/**
	 * 在发出请求时将SSLSocketFactory设置为用户。 默认情况下，使用一个新的默认SSLSocketFactory。
	 * 
	 * @param sslSocketFactory
	 *            sslSocketFactory用于https requests.
	 */
	public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory) {
		this.httpClient.getConnectionManager().getSchemeRegistry()
				.register(new Scheme("https", sslSocketFactory, 443));
	}

	/**
	 * 设置特定请求的最大重试次数和超时时间。
	 * 
	 * @param retries
	 *            每个请求的最大重试次数
	 * @param timeout
	 *            以毫秒为单位重试之间休眠时长
	 */
	public void setMaxRetriesAndTimeout(int retries, int timeout) {
		this.httpClient.setHttpRequestRetryHandler(new RetryHandler(retries,
				timeout));
	}

	/**
	 * 在发送之前，将删除AsyncHttpClient实例中当前存在的所有标头，适用于此客户端所有请求
	 */
	public void removeAllHeaders() {
		clientHeaderMap.clear();
	}

	/**
	 * 设置将添加到此客户端所做的所有请求（发送前）的头文件。
	 * 
	 * @param header
	 *            请求头名称
	 * @param value
	 *            请求头值
	 */
	public void addHeader(String header, String value) {
		clientHeaderMap.put(header, value);
	}

	/**
	 * 从客户端所发出的所有请求中删除头文件（发送前）。
	 * 
	 * @param header
	 *            这个请求头的名称
	 */
	public void removeHeader(String header) {
		clientHeaderMap.remove(header);
	}

	/**
	 * 设置请求的基本身份验证。 使用AuthScope.ANY<br>
	 * 这与setBasicAuth('username','password',AuthScope.ANY)相同
	 * 
	 * @param username
	 *            基本认证用户名
	 * @param password
	 *            基本认证密码
	 */
	public void setBasicAuth(String username, String password) {
		setBasicAuth(username, password, false);
	}

	/**
	 * 设置请求的基本身份验证。 使用AuthScope.ANY<br>
	 * 这与setBasicAuth('username','password',AuthScope.ANY)相同
	 * 
	 * @param username
	 *            基本认证用户名
	 * @param password
	 *            基本认证密码
	 * @param preemptive
	 *            以抢先的(preemptive)方式设定授权
	 */
	public void setBasicAuth(String username, String password,
			boolean preemptive) {
		setBasicAuth(username, password, null, preemptive);
	}

	/**
	 * 设置请求的基本身份验证<br>
	 * 为了安全起见，您应该传入您的AuthScope<br>
	 * 它应该像这样setBasicAuth("username","password", new AuthScope("host",port,AuthScope.ANY_REALM))
	 * 
	 * @param username
	 *            基本认证用户名
	 * @param password
	 *            基本认证密码
	 * @param scope
	 *            - an AuthScope object
	 */
	public void setBasicAuth(String username, String password, AuthScope scope) {
		setBasicAuth(username, password, scope, false);
	}

	/**
	 * 设置请求的基本身份验证<br>
	 * 为了安全起见，您应该传入您的AuthScope<br>
	 * 它应该像这样setBasicAuth("username","password", new AuthScope("host",port,AuthScope.ANY_REALM))
	 * 
	 * @param username
	 *            基本认证用户名
	 * @param password
	 *            基本认证密码
	 * @param scope
	 *            - an AuthScope object
	 * @param preemptive
	 *            以抢先的(preemptive)方式设定授权
	 */
	public void setBasicAuth(String username, String password, AuthScope scope,
			boolean preemptive) {
		UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
				username, password);
		setCredentials(scope, credentials);
		setAuthenticationPreemptive(preemptive);
	}

	public void setCredentials(AuthScope authScope, Credentials credentials) {
		if (credentials == null) {
			log.d(LOG_TAG, "Provided credentials are null, not setting");
			return;
		}
		this.httpClient.getCredentialsProvider().setCredentials(
				authScope == null ? AuthScope.ANY : authScope, credentials);
	}

	/**
	 * 设置以抢占方式处理授权的HttpRequestInterceptor<br>
	 * 作为解决方法，可以使用调用
	 * `AsyncHttpClient.addHeader("Authorization" ,"Basic base64OfUsernameAndPassword==")`
	 * 
	 * @param isPreemptive
	 *            授权是否以抢先的方式处理
	 */
	public void setAuthenticationPreemptive(boolean isPreemptive) {
		if (isPreemptive) {
			httpClient.addRequestInterceptor(
					new PreemptiveAuthorizationHttpRequestInterceptor(), 0);
		} else {
			httpClient
					.removeRequestInterceptorByClass(PreemptiveAuthorizationHttpRequestInterceptor.class);
		}
	}

	/**
	 * 删除以前设置的身份验证凭据
	 */
	public void clearCredentialsProvider() {
		this.httpClient.getCredentialsProvider().clear();
	}

	/**
	 * 取消与传递过来的指定Context相关联的任何待处理（或潜在的活动）请求。
	 * <p>
	 * &nbsp;
	 * </p>
	 * <b>Note:</b> 
	 * 这只会影响使用非空的Android上下文创建的请求。 <br>
	 * 该方法旨在用于您的Android活动的onDestroy方法，以销毁不再需要的所有请求。
	 * 
	 * @param context
	 *            与android Context实例关联的请求
	 * @param mayInterruptIfRunning
	 *            指定活动请求是否应与挂起的请求一起取消。
	 */
	public void cancelRequests(final Context context,
			final boolean mayInterruptIfRunning) {
		if (context == null) {
			log.e(LOG_TAG, "Passed null Context to cancelRequests");
			return;
		}

		final List<RequestHandle> requestList = requestMap.get(context);
		requestMap.remove(context);

		if (Looper.myLooper() == Looper.getMainLooper()) {
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					cancelRequests(requestList, mayInterruptIfRunning);
				}
			};
			threadPool.submit(runnable);
		} else {
			cancelRequests(requestList, mayInterruptIfRunning);
		}
	}

	private void cancelRequests(final List<RequestHandle> requestList,
			final boolean mayInterruptIfRunning) {
		if (requestList != null) {
			for (RequestHandle requestHandle : requestList) {
				requestHandle.cancel(mayInterruptIfRunning);
			}
		}
	}

	/**
	 * 取消所有待处理（或潜在的活动）请求。
	 * <p>
	 * &nbsp;
	 * </p>
	 * <b>Note:</b>
	 * 这只会影响使用非空的Android上下文创建的请求。 <br>
	 * 该方法旨在用于您的Android活动的onDestroy方法，以销毁不再需要的所有请求。
	 * 
	 * @param mayInterruptIfRunning
	 *            指定活动请求是否应与挂起的请求一起取消。
	 */
	public void cancelAllRequests(boolean mayInterruptIfRunning) {
		for (List<RequestHandle> requestList : requestMap.values()) {
			if (requestList != null) {
				for (RequestHandle requestHandle : requestList) {
					requestHandle.cancel(mayInterruptIfRunning);
				}
			}
		}
		requestMap.clear();
	}

	/**
	 * 允许您取消当前在队列或正在运行的所有请求，通过设置TAG，如果TAG为null，则不会尝试取消任何请求，如果TAG在RequestHandle为空
	 * ，则无法通过此呼叫取消
	 * 
	 * @param TAG
	 *            TAG要在RequestHandle中匹配
	 * @param mayInterruptIfRunning
	 *            指定活动请求是否应与挂起的请求一起取消。
	 */
	public void cancelRequestsByTAG(Object TAG, boolean mayInterruptIfRunning) {
		if (TAG == null) {
			log.d(LOG_TAG,
					"cancelRequestsByTAG, passed TAG is null, cannot proceed");
			return;
		}
		for (List<RequestHandle> requestList : requestMap.values()) {
			if (requestList != null) {
				for (RequestHandle requestHandle : requestList) {
					if (TAG.equals(requestHandle.getTag()))
						requestHandle.cancel(mayInterruptIfRunning);
				}
			}
		}
	}

	// [+] HTTP HEAD

	/**
	 * 执行HTTP HEAD请求，没有任何参数。
	 * 
	 * @param url
	 *            发送请求的URL。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle head(String url,
			ResponseHandlerInterface responseHandler) {
		return head(null, url, null, responseHandler);
	}

	/**
	 * 使用参数执行HTTP HEAD请求。
	 * 
	 * @param url
	 *            发送请求的URL。
	 * @param params
	 *            额外的HEAD参数要与请求一起发送。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle head(String url, RequestParams params,
			ResponseHandlerInterface responseHandler) {
		return head(null, url, params, responseHandler);
	}

	/**
	 * 执行没有任何参数的HTTP HEAD请求，并跟踪启动请求的Android上下文。
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle head(Context context, String url,
			ResponseHandlerInterface responseHandler) {
		return head(context, url, null, responseHandler);
	}

	/**
	 * 执行HTTP HEAD请求并跟踪启动请求的Android上下文。
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param params
	 *            额外的HEAD参数要与请求一起发送。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle head(Context context, String url,
			RequestParams params, ResponseHandlerInterface responseHandler) {
		return sendRequest(httpClient, httpContext, new HttpHead(
				getUrlWithQueryString(isUrlEncodingEnabled, url, params)),
				null, responseHandler, context);
	}

	/**
	 * 执行HTTP HEAD请求并跟踪启动请求的Android上下文。
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param headers
	 *            仅为此请求设置请求头
	 * @param params
	 *            额外的HEAD参数要与请求一起发送。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle head(Context context, String url, Header[] headers,
			RequestParams params, ResponseHandlerInterface responseHandler) {
		HttpUriRequest request = new HttpHead(getUrlWithQueryString(
				isUrlEncodingEnabled, url, params));
		if (headers != null)
			request.setHeaders(headers);
		return sendRequest(httpClient, httpContext, request, null,
				responseHandler, context);
	}

	// [-] HTTP HEAD
	// [+] HTTP GET

	/**
	 * 执行HTTP GET请求，没有任何参数。
	 * 
	 * @param url
	 *            发送请求的URL。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle get(String url,
			ResponseHandlerInterface responseHandler) {
		return get(null, url, null, responseHandler);
	}

	/**
	 * 使用参数执行HTTP GET请求。
	 * 
	 * @param url
	 *            发送请求的URL。
	 * @param params
	 *            额外的GET参数要与请求一起发送。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle get(String url, RequestParams params,
			ResponseHandlerInterface responseHandler) {
		return get(null, url, params, responseHandler);
	}

	/**
	 * 执行没有任何参数的HTTP GET请求，并跟踪启动请求的Android上下文。
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle get(Context context, String url,
			ResponseHandlerInterface responseHandler) {
		return get(context, url, null, responseHandler);
	}

	/**
	 * 执行HTTP GET请求并跟踪启动请求的Android上下文。
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param params
	 *            额外的GET参数要与请求一起发送。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle get(Context context, String url, RequestParams params,
			ResponseHandlerInterface responseHandler) {
		return sendRequest(httpClient, httpContext, new HttpGet(
				getUrlWithQueryString(isUrlEncodingEnabled, url, params)),
				null, responseHandler, context);
	}

	/**
	 * 执行HTTP GET请求，并跟踪使用自定义请求头启动请求的Android上下文
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param headers
	 *            只为此请求设置请求头
	 * @param params
	 *            额外的GET参数要与请求一起发送。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle get(Context context, String url, Header[] headers,
			RequestParams params, ResponseHandlerInterface responseHandler) {
		HttpUriRequest request = new HttpGet(getUrlWithQueryString(
				isUrlEncodingEnabled, url, params));
		if (headers != null)
			request.setHeaders(headers);
		return sendRequest(httpClient, httpContext, request, null,
				responseHandler, context);
	}

	/**
	 * 执行HTTP GET请求并跟踪启动请求的Android上下文。
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param entity
	 *            一个原始的{@link org.apache.http.HttpEntity}来发送请求，<br>
	 *            例如，使用它来通过传递一个{@link org.apache.http.entity.StringEntity}
	 *            将string/json/xml有效载荷发送到服务器。
	 * @param contentType
	 *            您要发送的有效载荷的内容类型，<br>
	 *            例如，如果发送json有效载荷，则为application/json
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle get(Context context, String url, HttpEntity entity,
			String contentType, ResponseHandlerInterface responseHandler) {
		return sendRequest(
				httpClient,
				httpContext,
				addEntityToRequestBase(
						new HttpGet(URI.create(url).normalize()), entity),
				contentType, responseHandler, context);
	}

	// [-] HTTP GET
	// [+] HTTP POST

	/**
	 * 执行HTTP POST请求，没有任何参数。
	 * 
	 * @param url
	 *            发送请求的URL。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle post(String url,
			ResponseHandlerInterface responseHandler) {
		return post(null, url, null, responseHandler);
	}

	/**
	 * 使用参数执行HTTP POST请求。
	 * 
	 * @param url
	 *            发送请求的URL。
	 * @param params
	 *            额外的POST参数或与请求一起发送的文件。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle post(String url, RequestParams params,
			ResponseHandlerInterface responseHandler) {
		return post(null, url, params, responseHandler);
	}

	/**
	 * 执行HTTP POST请求并跟踪启动请求的Android上下文。
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param params
	 *            额外的POST参数或与请求一起发送的文件。
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle post(Context context, String url,
			RequestParams params, ResponseHandlerInterface responseHandler) {
		return post(context, url, paramsToEntity(params, responseHandler),
				null, responseHandler);
	}

	/**
	 * 执行HTTP POST请求并跟踪启动请求的Android上下文。
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param entity
	 *            一个原始的{@link org.apache.http.HttpEntity}来发送请求，<br>
	 *            例如，使用它来通过传递一个{@link org.apache.http.entity.StringEntity}
	 *            将string/json/xml有效载荷发送到服务器。
	 * @param contentType
	 *            您要发送的有效载荷的内容类型，<br>
	 *            例如，如果发送json有效载荷，则为application/json
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle post(Context context, String url, HttpEntity entity,
			String contentType, ResponseHandlerInterface responseHandler) {
		return sendRequest(httpClient, httpContext,
				addEntityToRequestBase(new HttpPost(getURI(url)), entity),
				contentType, responseHandler, context);
	}

	/**
	 * 执行HTTP POST请求并跟踪启动请求的Android上下文。 仅为此请求设置请求头
	 * 
	 * @param context
	 *            发起请求的Android上下文。
	 * @param url
	 *            发送请求的URL。
	 * @param headers
	 *            仅为此请求设置请求头
	 * @param params
	 *            额外的POST参数或与请求一起发送的文件。
	 * @param contentType
	 *            您要发送的有效载荷的内容类型，<br>
	 *            例如，如果发送json有效载荷，则为application/json
	 * @param responseHandler
	 *            响应处理程序实例应该处理响应。
	 * @return RequestHandle的未来请求process
	 */
	public RequestHandle post(Context context, String url, Header[] headers,
			RequestParams params, String contentType,
			ResponseHandlerInterface responseHandler) {
		HttpEntityEnclosingRequestBase request = new HttpPost(getURI(url));
		if (params != null)
			request.setEntity(paramsToEntity(params, responseHandler));
		if (headers != null)
			request.setHeaders(headers);
		return sendRequest(httpClient, httpContext, request, contentType,
				responseHandler, context);
	}

	/**
	 * Perform a HTTP POST request and track the Android Context which initiated
	 * the request. Set headers only for this request
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param headers
	 *            set headers only for this request
	 * @param entity
	 *            a raw {@link HttpEntity} to send with the request, for
	 *            example, use this to send string/json/xml payloads to a server
	 *            by passing a {@link org.apache.http.entity.StringEntity}.
	 * @param contentType
	 *            the content type of the payload you are sending, for example
	 *            application/json if sending a json payload.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle post(Context context, String url, Header[] headers,
			HttpEntity entity, String contentType,
			ResponseHandlerInterface responseHandler) {
		HttpEntityEnclosingRequestBase request = addEntityToRequestBase(
				new HttpPost(getURI(url)), entity);
		if (headers != null)
			request.setHeaders(headers);
		return sendRequest(httpClient, httpContext, request, contentType,
				responseHandler, context);
	}

	// [-] HTTP POST
	// [+] HTTP PUT

	/**
	 * Perform a HTTP PUT request, without any parameters.
	 * 
	 * @param url
	 *            the URL to send the request to.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle put(String url,
			ResponseHandlerInterface responseHandler) {
		return put(null, url, null, responseHandler);
	}

	/**
	 * Perform a HTTP PUT request with parameters.
	 * 
	 * @param url
	 *            the URL to send the request to.
	 * @param params
	 *            additional PUT parameters or files to send with the request.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle put(String url, RequestParams params,
			ResponseHandlerInterface responseHandler) {
		return put(null, url, params, responseHandler);
	}

	/**
	 * Perform a HTTP PUT request and track the Android Context which initiated
	 * the request.
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param params
	 *            additional PUT parameters or files to send with the request.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle put(Context context, String url, RequestParams params,
			ResponseHandlerInterface responseHandler) {
		return put(context, url, paramsToEntity(params, responseHandler), null,
				responseHandler);
	}

	/**
	 * Perform a HTTP PUT request and track the Android Context which initiated
	 * the request. And set one-time headers for the request
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param entity
	 *            a raw {@link HttpEntity} to send with the request, for
	 *            example, use this to send string/json/xml payloads to a server
	 *            by passing a {@link org.apache.http.entity.StringEntity}.
	 * @param contentType
	 *            the content type of the payload you are sending, for example
	 *            application/json if sending a json payload.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle put(Context context, String url, HttpEntity entity,
			String contentType, ResponseHandlerInterface responseHandler) {
		return sendRequest(httpClient, httpContext,
				addEntityToRequestBase(new HttpPut(getURI(url)), entity),
				contentType, responseHandler, context);
	}

	/**
	 * Perform a HTTP PUT request and track the Android Context which initiated
	 * the request. And set one-time headers for the request
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param headers
	 *            set one-time headers for this request
	 * @param entity
	 *            a raw {@link HttpEntity} to send with the request, for
	 *            example, use this to send string/json/xml payloads to a server
	 *            by passing a {@link org.apache.http.entity.StringEntity}.
	 * @param contentType
	 *            the content type of the payload you are sending, for example
	 *            application/json if sending a json payload.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle put(Context context, String url, Header[] headers,
			HttpEntity entity, String contentType,
			ResponseHandlerInterface responseHandler) {
		HttpEntityEnclosingRequestBase request = addEntityToRequestBase(
				new HttpPut(getURI(url)), entity);
		if (headers != null)
			request.setHeaders(headers);
		return sendRequest(httpClient, httpContext, request, contentType,
				responseHandler, context);
	}

	/**
	 * Perform a HTTP request, without any parameters.
	 * 
	 * @param url
	 *            the URL to send the request to.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle patch(String url,
			ResponseHandlerInterface responseHandler) {
		return patch(null, url, null, responseHandler);
	}

	/**
	 * Perform a HTTP PATCH request with parameters.
	 * 
	 * @param url
	 *            the URL to send the request to.
	 * @param params
	 *            additional PUT parameters or files to send with the request.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle patch(String url, RequestParams params,
			ResponseHandlerInterface responseHandler) {
		return patch(null, url, params, responseHandler);
	}

	/**
	 * Perform a HTTP PATCH request and track the Android Context which
	 * initiated the request.
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param params
	 *            additional PUT parameters or files to send with the request.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle patch(Context context, String url,
			RequestParams params, ResponseHandlerInterface responseHandler) {
		return patch(context, url, paramsToEntity(params, responseHandler),
				null, responseHandler);
	}

	/**
	 * Perform a HTTP PATCH request and track the Android Context which
	 * initiated the request.
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @param entity
	 *            a raw {@link HttpEntity} to send with the request, for
	 *            example, use this to send string/json/xml payloads to a server
	 *            by passing a {@link org.apache.http.entity.StringEntity}
	 * @param contentType
	 *            the content type of the payload you are sending, for example
	 *            "application/json" if sending a json payload.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle patch(Context context, String url, HttpEntity entity,
			String contentType, ResponseHandlerInterface responseHandler) {
		return sendRequest(httpClient, httpContext,
				addEntityToRequestBase(new HttpPatch(getURI(url)), entity),
				contentType, responseHandler, context);
	}

	/**
	 * Perform a HTTP PATCH request and track the Android Context which
	 * initiated the request. And set one-time headers for the request
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param headers
	 *            set one-time headers for this request
	 * @param entity
	 *            a raw {@link HttpEntity} to send with the request, for
	 *            example, use this to send string/json/xml payloads to a server
	 *            by passing a {@link org.apache.http.entity.StringEntity}.
	 * @param contentType
	 *            the content type of the payload you are sending, for example
	 *            application/json if sending a json payload.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle patch(Context context, String url, Header[] headers,
			HttpEntity entity, String contentType,
			ResponseHandlerInterface responseHandler) {
		HttpEntityEnclosingRequestBase request = addEntityToRequestBase(
				new HttpPatch(getURI(url)), entity);
		if (headers != null)
			request.setHeaders(headers);
		return sendRequest(httpClient, httpContext, request, contentType,
				responseHandler, context);
	}

	// [-] HTTP PUT
	// [+] HTTP DELETE

	/**
	 * Perform a HTTP DELETE request.
	 * 
	 * @param url
	 *            the URL to send the request to.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle delete(String url,
			ResponseHandlerInterface responseHandler) {
		return delete(null, url, responseHandler);
	}

	/**
	 * Perform a HTTP DELETE request.
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle delete(Context context, String url,
			ResponseHandlerInterface responseHandler) {
		final HttpDelete delete = new HttpDelete(getURI(url));
		return sendRequest(httpClient, httpContext, delete, null,
				responseHandler, context);
	}

	/**
	 * Perform a HTTP DELETE request.
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param headers
	 *            set one-time headers for this request
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle delete(Context context, String url, Header[] headers,
			ResponseHandlerInterface responseHandler) {
		final HttpDelete delete = new HttpDelete(getURI(url));
		if (headers != null)
			delete.setHeaders(headers);
		return sendRequest(httpClient, httpContext, delete, null,
				responseHandler, context);
	}

	/**
	 * Perform a HTTP DELETE request.
	 * 
	 * @param url
	 *            the URL to send the request to.
	 * @param params
	 *            additional DELETE parameters or files to send with the
	 *            request.
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 */
	public void delete(String url, RequestParams params,
			AsyncHttpResponseHandler responseHandler) {
		final HttpDelete delete = new HttpDelete(getUrlWithQueryString(
				isUrlEncodingEnabled, url, params));
		sendRequest(httpClient, httpContext, delete, null, responseHandler,
				null);
	}

	/**
	 * Perform a HTTP DELETE request.
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param headers
	 *            set one-time headers for this request
	 * @param params
	 *            additional DELETE parameters or files to send along with
	 *            request
	 * @param responseHandler
	 *            the response handler instance that should handle the response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle delete(Context context, String url, Header[] headers,
			RequestParams params, ResponseHandlerInterface responseHandler) {
		HttpDelete httpDelete = new HttpDelete(getUrlWithQueryString(
				isUrlEncodingEnabled, url, params));
		if (headers != null)
			httpDelete.setHeaders(headers);
		return sendRequest(httpClient, httpContext, httpDelete, null,
				responseHandler, context);
	}

	/**
	 * Perform a HTTP DELETE request and track the Android Context which
	 * initiated the request.
	 * 
	 * @param context
	 *            the Android Context which initiated the request.
	 * @param url
	 *            the URL to send the request to.
	 * @param entity
	 *            a raw {@link org.apache.http.HttpEntity} to send with the
	 *            request, for example, use this to send string/json/xml
	 *            payloads to a server by passing a
	 *            {@link org.apache.http.entity.StringEntity}.
	 * @param contentType
	 *            the content type of the payload you are sending, for example
	 *            application/json if sending a json payload.
	 * @param responseHandler
	 *            the response ha ndler instance that should handle the
	 *            response.
	 * @return RequestHandle of future request process
	 */
	public RequestHandle delete(Context context, String url, HttpEntity entity,
			String contentType, ResponseHandlerInterface responseHandler) {
		return sendRequest(
				httpClient,
				httpContext,
				addEntityToRequestBase(new HttpDelete(URI.create(url)
						.normalize()), entity), contentType, responseHandler,
				context);
	}

	// [-] HTTP DELETE

	/**
	 * Instantiate a new asynchronous HTTP request for the passed parameters.
	 * 
	 * @param client
	 *            HttpClient to be used for request, can differ in single
	 *            requests
	 * @param contentType
	 *            MIME body type, for POST and PUT requests, may be null
	 * @param context
	 *            Context of Android application, to hold the reference of
	 *            request
	 * @param httpContext
	 *            HttpContext in which the request will be executed
	 * @param responseHandler
	 *            ResponseHandler or its subclass to put the response into
	 * @param uriRequest
	 *            instance of HttpUriRequest, which means it must be of
	 *            HttpDelete, HttpPost, HttpGet, HttpPut, etc.
	 * @return AsyncHttpRequest ready to be dispatched
	 */
	protected AsyncHttpRequest newAsyncHttpRequest(DefaultHttpClient client,
			HttpContext httpContext, HttpUriRequest uriRequest,
			String contentType, ResponseHandlerInterface responseHandler,
			Context context) {
		return new AsyncHttpRequest(client, httpContext, uriRequest,
				responseHandler);
	}

	/**
	 * Puts a new request in queue as a new thread in pool to be executed
	 * 
	 * @param client
	 *            HttpClient to be used for request, can differ in single
	 *            requests
	 * @param contentType
	 *            MIME body type, for POST and PUT requests, may be null
	 * @param context
	 *            Context of Android application, to hold the reference of
	 *            request
	 * @param httpContext
	 *            HttpContext in which the request will be executed
	 * @param responseHandler
	 *            ResponseHandler or its subclass to put the response into
	 * @param uriRequest
	 *            instance of HttpUriRequest, which means it must be of
	 *            HttpDelete, HttpPost, HttpGet, HttpPut, etc.
	 * @return RequestHandle of future request process
	 */
	protected RequestHandle sendRequest(DefaultHttpClient client,
			HttpContext httpContext, HttpUriRequest uriRequest,
			String contentType, ResponseHandlerInterface responseHandler,
			Context context) {
		if (uriRequest == null) {
			throw new IllegalArgumentException(
					"HttpUriRequest must not be null");
		}

		if (responseHandler == null) {
			throw new IllegalArgumentException(
					"ResponseHandler must not be null");
		}

		if (responseHandler.getUseSynchronousMode()
				&& !responseHandler.getUsePoolThread()) {
			throw new IllegalArgumentException(
					"Synchronous ResponseHandler used in AsyncHttpClient. You should create your response handler in a looper thread or use SyncHttpClient instead.");
		}

		if (contentType != null) {
			if (uriRequest instanceof HttpEntityEnclosingRequestBase
					&& ((HttpEntityEnclosingRequestBase) uriRequest)
							.getEntity() != null
					&& uriRequest.containsHeader(HEADER_CONTENT_TYPE)) {
				log.w(LOG_TAG,
						"Passed contentType will be ignored because HttpEntity sets content type");
			} else {
				uriRequest.setHeader(HEADER_CONTENT_TYPE, contentType);
			}
		}

		responseHandler.setRequestHeaders(uriRequest.getAllHeaders());
		responseHandler.setRequestURI(uriRequest.getURI());

		AsyncHttpRequest request = newAsyncHttpRequest(client, httpContext,
				uriRequest, contentType, responseHandler, context);
		threadPool.submit(request);
		RequestHandle requestHandle = new RequestHandle(request);

		if (context != null) {
			List<RequestHandle> requestList;
			// Add request to request map
			synchronized (requestMap) {
				requestList = requestMap.get(context);
				if (requestList == null) {
					requestList = Collections
							.synchronizedList(new LinkedList<RequestHandle>());
					requestMap.put(context, requestList);
				}
			}

			requestList.add(requestHandle);

			Iterator<RequestHandle> iterator = requestList.iterator();
			while (iterator.hasNext()) {
				if (iterator.next().shouldBeGarbageCollected()) {
					iterator.remove();
				}
			}
		}

		return requestHandle;
	}

	/**
	 * Returns a {@link URI} instance for the specified, absolute URL string.
	 * 
	 * @param url
	 *            absolute URL string, containing scheme, host and path
	 * @return URI instance for the URL string
	 */
	protected URI getURI(String url) {
		return URI.create(url).normalize();
	}

	/**
	 * Sets state of URL encoding feature, see bug #227, this method allows you
	 * to turn off and on this auto-magic feature on-demand.
	 * 
	 * @param enabled
	 *            desired state of feature
	 */
	public void setURLEncodingEnabled(boolean enabled) {
		this.isUrlEncodingEnabled = enabled;
	}

	/**
	 * 将编码url，如果没有禁用，并在其末尾添加参数
	 * 
	 * @param url
	 *            带有URL的字符串，应该是没有参数的有效URL
	 * @param params
	 *            请求附加在URL结尾的RequestParams
	 * @param shouldEncodeUrl
	 *            是否应编码url（用％20替换空格）
	 * @return 编码的url，如果需要，附加参数，如果有的话
	 */
	public static String getUrlWithQueryString(boolean shouldEncodeUrl,
			String url, RequestParams params) {
		if (url == null)
			return null;

		if (shouldEncodeUrl) {
			try {
				String decodedURL = URLDecoder.decode(url, "UTF-8");
				URL _url = new URL(decodedURL);
				URI _uri = new URI(_url.getProtocol(), _url.getUserInfo(),
						_url.getHost(), _url.getPort(), _url.getPath(),
						_url.getQuery(), _url.getRef());
				url = _uri.toASCIIString();
			} catch (Exception ex) {
				// Should not really happen, added just for sake of validity
				log.e(LOG_TAG, "getUrlWithQueryString encoding URL", ex);
			}
		}

		if (params != null) {
			// 构造查询字符串并修剪它，以防包含任何过多的空格。
			String paramString = params.getParamString().trim();

			// 只有在查询字符串不为空并且不等于'?'时才添加。
			if (!paramString.equals("") && !paramString.equals("?")) {
				url += url.contains("?") ? "&" : "?";
				url += paramString;
			}
		}

		return url;
	}

	/**
	 * Checks the InputStream if it contains GZIP compressed data
	 * 
	 * @param inputStream
	 *            InputStream to be checked
	 * @return true or false if the stream contains GZIP compressed data
	 * @throws java.io.IOException
	 *             if read from inputStream fails
	 */
	public static boolean isInputStreamGZIPCompressed(
			final PushbackInputStream inputStream) throws IOException {
		if (inputStream == null)
			return false;

		byte[] signature = new byte[2];
		int readStatus = inputStream.read(signature);
		inputStream.unread(signature);
		int streamHeader = ((int) signature[0] & 0xff)
				| ((signature[1] << 8) & 0xff00);
		return readStatus == 2 && GZIPInputStream.GZIP_MAGIC == streamHeader;
	}

	/**
	 * 静默关闭输入流
	 * 
	 * @param is
	 *            input stream to close safely
	 */
	public static void silentCloseInputStream(InputStream is) {
		try {
			if (is != null) {
				is.close();
			}
		} catch (IOException e) {
			log.w(LOG_TAG, "Cannot close input stream", e);
		}
	}

	/**
	 * 静默关闭输出流
	 * 
	 * @param os
	 *            output stream to close safely
	 */
	public static void silentCloseOutputStream(OutputStream os) {
		try {
			if (os != null) {
				os.close();
			}
		} catch (IOException e) {
			log.w(LOG_TAG, "Cannot close output stream", e);
		}
	}

	/**
	 * 返回包含请求声明中包含的RequestParams中的数据的HttpEntity。 <br>
	 * 允许通过提供的ResponseHandler从上传传递进度
	 * Returns HttpEntity containing data from RequestParams included with
	 * request declaration. Allows also passing progress from upload via
	 * provided ResponseHandler
	 * 
	 * @param params
	 *            额外的请求参数
	 * @param responseHandler
	 *            ResponseHandlerInterface或其子类进行通知
	 */
	private HttpEntity paramsToEntity(RequestParams params,
			ResponseHandlerInterface responseHandler) {
		HttpEntity entity = null;

		try {
			if (params != null) {
				entity = params.getEntity(responseHandler);
			}
		} catch (IOException e) {
			if (responseHandler != null) {
				responseHandler.sendFailureMessage(0, null, null, e);
			} else {
				e.printStackTrace();
			}
		}

		return entity;
	}

	public boolean isUrlEncodingEnabled() {
		return isUrlEncodingEnabled;
	}

	/**
	 * Applicable only to HttpRequest methods extending
	 * HttpEntityEnclosingRequestBase, which is for example not DELETE
	 * 
	 * @param entity
	 *            entity to be included within the request
	 * @param requestBase
	 *            HttpRequest instance, must not be null
	 */
	private HttpEntityEnclosingRequestBase addEntityToRequestBase(
			HttpEntityEnclosingRequestBase requestBase, HttpEntity entity) {
		if (entity != null) {
			requestBase.setEntity(entity);
		}

		return requestBase;
	}

	/**
	 * This horrible hack is required on Android, due to implementation of
	 * BasicManagedEntity, which doesn't chain call consumeContent on underlying
	 * wrapped HttpEntity
	 * 
	 * @param entity
	 *            HttpEntity, may be null
	 */
	public static void endEntityViaReflection(HttpEntity entity) {
		if (entity instanceof HttpEntityWrapper) {
			try {
				Field f = null;
				Field[] fields = HttpEntityWrapper.class.getDeclaredFields();
				for (Field ff : fields) {
					if (ff.getName().equals("wrappedEntity")) {
						f = ff;
						break;
					}
				}
				if (f != null) {
					f.setAccessible(true);
					HttpEntity wrapped = (HttpEntity) f.get(entity);
					if (wrapped != null) {
						wrapped.consumeContent();
					}
				}
			} catch (Throwable t) {
				log.e(LOG_TAG, "wrappedEntity consume", t);
			}
		}
	}

	/**
	 * 填充实体<br>
	 * Enclosing entity to hold stream of gzip decoded data for accessing
	 * HttpEntity contents
	 */
	private static class InflatingEntity extends HttpEntityWrapper {

		public InflatingEntity(HttpEntity wrapped) {
			super(wrapped);
		}

		InputStream wrappedStream;// 包裹流
		PushbackInputStream pushbackStream;
		GZIPInputStream gzippedStream;

		@Override
		public InputStream getContent() throws IOException {
			wrappedStream = wrappedEntity.getContent();
			pushbackStream = new PushbackInputStream(wrappedStream, 2);
			if (isInputStreamGZIPCompressed(pushbackStream)) {
				gzippedStream = new GZIPInputStream(pushbackStream);
				return gzippedStream;
			} else {
				return pushbackStream;
			}
		}

		@Override
		public long getContentLength() {
			return wrappedEntity == null ? 0 : wrappedEntity.getContentLength();
		}

		@Override
		public void consumeContent() throws IOException {
			// 静默关闭输入流，以释放内存
			AsyncHttpClient.silentCloseInputStream(wrappedStream);
			AsyncHttpClient.silentCloseInputStream(pushbackStream);
			AsyncHttpClient.silentCloseInputStream(gzippedStream);
			super.consumeContent();
		}
	}
}
