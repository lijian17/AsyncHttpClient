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

/**
 * 提供一个常规断言工具<br>
 * 它们在编译/运行时被Android SDK剥离，用于发布版本
 */
class Utils {

	private Utils() {
	}

	/**
	 * 如果表达式不返回true，则抛出AssertionError
	 * 
	 * @param expression
	 *            你的断言条件的结果
	 * @param failedMessage
	 *            错误消息（消息被包含在错误日志中）
	 * @throws java.lang.AssertionError
	 */
	public static void asserts(final boolean expression,
			final String failedMessage) {
		if (!expression) {
			throw new AssertionError(failedMessage);
		}
	}

	/**
	 * 如果提供的object在运行时为空，则将抛出IllegalArgumentException异常
	 * 
	 * @param argument
	 *            待验证是否为null的object
	 * @param name
	 *            当object为null时，抛出异常时的名称（所声明的对象的名称）
	 * @throws java.lang.IllegalArgumentException
	 */
	public static <T> T notNull(final T argument, final String name) {
		if (argument == null) {
			throw new IllegalArgumentException(name + " 不应该为null!");
		}
		return argument;
	}
}
