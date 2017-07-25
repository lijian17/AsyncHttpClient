package com.loopj.android.http;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;

/**
 * log处理器
 * 
 * @author lijian-pc
 * @date 2017-7-25 上午11:12:25
 */
public class LogHandler implements LogInterface {

	boolean mLoggingEnabled = true;
	int mLoggingLevel = VERBOSE;

	@Override
	public boolean isLoggingEnabled() {
		return mLoggingEnabled;
	}

	@Override
	public void setLoggingEnabled(boolean loggingEnabled) {
		this.mLoggingEnabled = loggingEnabled;
	}

	@Override
	public int getLoggingLevel() {
		return mLoggingLevel;
	}

	@Override
	public void setLoggingLevel(int loggingLevel) {
		this.mLoggingLevel = loggingLevel;
	}

	@Override
	public boolean shouldLog(int logLevel) {
		return logLevel >= mLoggingLevel;
	}

	/**
	 * 不处理异常的日志
	 * 
	 * @param logLevel
	 * @param tag
	 * @param msg
	 */
	public void log(int logLevel, String tag, String msg) {
		logWithThrowable(logLevel, tag, msg, null);
	}

	/**
	 * 处理异常的日志
	 * 
	 * @param logLevel
	 * @param tag
	 * @param msg
	 * @param t
	 */
	public void logWithThrowable(int logLevel, String tag, String msg,
			Throwable t) {
		if (isLoggingEnabled() && shouldLog(logLevel)) {
			switch (logLevel) {
			case VERBOSE:
				Log.v(tag, msg, t);
				break;
			case WARN:
				Log.w(tag, msg, t);
				break;
			case ERROR:
				Log.e(tag, msg, t);
				break;
			case DEBUG:
				Log.d(tag, msg, t);
				break;
			case WTF:
				if (Integer.valueOf(Build.VERSION.SDK) > 8) {
					checkedWtf(tag, msg, t);
				} else {
					Log.e(tag, msg, t);
				}
				break;
			case INFO:
				Log.i(tag, msg, t);
				break;
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.FROYO)
	private void checkedWtf(String tag, String msg, Throwable t) {
		/**
		 * 我们可以直接使用android提供的android.util.Log.wtf()系列函数来输出一个日志.在输出日志的同时，
		 * 它会把此处代码此时的执行路径(调用栈)打印出来。
		 */
		Log.wtf(tag, msg, t);
	}

	@Override
	public void v(String tag, String msg) {
		log(VERBOSE, tag, msg);
	}

	@Override
	public void v(String tag, String msg, Throwable t) {
		logWithThrowable(VERBOSE, tag, msg, t);
	}

	@Override
	public void d(String tag, String msg) {
		log(VERBOSE, tag, msg);
	}

	@Override
	public void d(String tag, String msg, Throwable t) {
		logWithThrowable(DEBUG, tag, msg, t);
	}

	@Override
	public void i(String tag, String msg) {
		log(INFO, tag, msg);
	}

	@Override
	public void i(String tag, String msg, Throwable t) {
		logWithThrowable(INFO, tag, msg, t);
	}

	@Override
	public void w(String tag, String msg) {
		log(WARN, tag, msg);
	}

	@Override
	public void w(String tag, String msg, Throwable t) {
		logWithThrowable(WARN, tag, msg, t);
	}

	@Override
	public void e(String tag, String msg) {
		log(ERROR, tag, msg);
	}

	@Override
	public void e(String tag, String msg, Throwable t) {
		logWithThrowable(ERROR, tag, msg, t);
	}

	@Override
	public void wtf(String tag, String msg) {
		log(WTF, tag, msg);
	}

	@Override
	public void wtf(String tag, String msg, Throwable t) {
		logWithThrowable(WTF, tag, msg, t);
	}
}
