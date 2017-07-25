package com.loopj.android.http;

/**
 * 独立于任何库的Interface，目前使用了{@link android.util.Log}做的实现。<br>
 * 你可以改变目前使用的LogInterface，通过{@link AsyncHttpClient#setLogInterface(LogInterface)}
 */
public interface LogInterface {

	/** 日志等级 */
    int VERBOSE = 2;
    int DEBUG = 3;
    int INFO = 4;
    int WARN = 5;
    int ERROR = 6;
    int WTF = 8;

    /** 日志是否激活 */
    boolean isLoggingEnabled();

    /** 设置日志是否激活 */
    void setLoggingEnabled(boolean loggingEnabled);

    /** 获取当前日志等级 */
    int getLoggingLevel();

    /** 设置日志等级 */
    void setLoggingLevel(int loggingLevel);

    /** 当前日志是否在可处理的等级内 */
    boolean shouldLog(int logLevel);

    void v(String tag, String msg);

    void v(String tag, String msg, Throwable t);

    void d(String tag, String msg);

    void d(String tag, String msg, Throwable t);

    void i(String tag, String msg);

    void i(String tag, String msg, Throwable t);

    void w(String tag, String msg);

    void w(String tag, String msg, Throwable t);

    void e(String tag, String msg);

    void e(String tag, String msg, Throwable t);

    void wtf(String tag, String msg);

    void wtf(String tag, String msg, Throwable t);

}
