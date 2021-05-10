/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Default implementation of the {@link NamespaceHandlerResolver} interface.
 * Resolves namespace URIs to implementation classes based on the mappings
 * contained in mapping file.
 *
 * <p>By default, this implementation looks for the mapping file at
 * {@code META-INF/spring.handlers}, but this can be changed using the
 * {@link #DefaultNamespaceHandlerResolver(ClassLoader, String)} constructor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 * @see NamespaceHandler
 * @see DefaultBeanDefinitionDocumentReader
 */
public class DefaultNamespaceHandlerResolver implements NamespaceHandlerResolver {

	/**
	 * The location to look for the mapping files. Can be present in multiple JAR files.
	 */
	public static final String DEFAULT_HANDLER_MAPPINGS_LOCATION = "META-INF/spring.handlers";


	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/** ClassLoader to use for NamespaceHandler classes. */
	@Nullable
	private final ClassLoader classLoader;

	/** Resource location to search for. */
	private final String handlerMappingsLocation;

	/** Stores the mappings from namespace URI to NamespaceHandler class name / instance. */
	@Nullable
	private volatile Map<String, Object> handlerMappings;


	/**
	 * Create a new {@code DefaultNamespaceHandlerResolver} using the
	 * default mapping file location.
	 * <p>This constructor will result in the thread context ClassLoader being used
	 * to load resources.
	 * @see #DEFAULT_HANDLER_MAPPINGS_LOCATION
	 */
	public DefaultNamespaceHandlerResolver() {
		this(null, DEFAULT_HANDLER_MAPPINGS_LOCATION);
	}

	/**
	 * Create a new {@code DefaultNamespaceHandlerResolver} using the
	 * default mapping file location.
	 * @param classLoader the {@link ClassLoader} instance used to load mapping resources
	 * (may be {@code null}, in which case the thread context ClassLoader will be used)
	 * @see #DEFAULT_HANDLER_MAPPINGS_LOCATION
	 */
	public DefaultNamespaceHandlerResolver(@Nullable ClassLoader classLoader) {
		// DEFAULT_HANDLER_MAPPINGS_LOCATION = "META-INF/spring.handlers";
		this(classLoader, DEFAULT_HANDLER_MAPPINGS_LOCATION);
	}

	/**
	 * Create a new {@code DefaultNamespaceHandlerResolver} using the
	 * supplied mapping file location.
	 * @param classLoader the {@link ClassLoader} instance used to load mapping resources
	 * may be {@code null}, in which case the thread context ClassLoader will be used)
	 * @param handlerMappingsLocation the mapping file location
	 */
	public DefaultNamespaceHandlerResolver(@Nullable ClassLoader classLoader, String handlerMappingsLocation) {
		Assert.notNull(handlerMappingsLocation, "Handler mappings location must not be null");
		this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
		this.handlerMappingsLocation = handlerMappingsLocation;
	}


	/**
	 * Locate the {@link NamespaceHandler} for the supplied namespace URI
	 * from the configured mappings.
	 * @param namespaceUri the relevant namespace URI
	 * @return the located {@link NamespaceHandler}, or {@code null} if none found
	 */
	@Override
	@Nullable
	public NamespaceHandler resolve(String namespaceUri) {
		// 获取所有已经配置的 handler 映射
		// 拿到配置文件的所有命名空间和对应的handler
		// 例如："http://www.springframework.org/schema/aop" -> "org.springframework.aop.config.AopNamespaceHandler"
		Map<String, Object> handlerMappings = getHandlerMappings();
		// 根据命名空间找到对应的信息
		// 拿到当前命名空间对应的handler （可能是handler的className，也可能是已经实例化的handler）
		Object handlerOrClassName = handlerMappings.get(namespaceUri);
		if (handlerOrClassName == null) {
			// 如果不存在namespaceUri对应的handler，则返回null
			return null;
		}
		else if (handlerOrClassName instanceof NamespaceHandler) {
			// 已经解析过的情况，直接从缓存中读取
			// 如果是已经实例化的handler，则直接强转返回
			return (NamespaceHandler) handlerOrClassName;
		}
		else {
			// 没有做过解析，则返回的是类路径
			String className = (String) handlerOrClassName;
			try {
				// 使用反射将类路径转化为类
				// 根据className，使用类加载器拿到该类
				Class<?> handlerClass = ClassUtils.forName(className, this.classLoader);
				// 校验是否是继承自NamespaceHandler（所有的handler都继承自NamespaceHandler）
				if (!NamespaceHandler.class.isAssignableFrom(handlerClass)) {
					throw new FatalBeanException("Class [" + className + "] for namespace [" + namespaceUri +
							"] does not implement the [" + NamespaceHandler.class.getName() + "] interface");
				}
				// 初始化类，使用无参构造函数实例化handlerClass类
				NamespaceHandler namespaceHandler = (NamespaceHandler) BeanUtils.instantiateClass(handlerClass);
				// 调用自定义的 NamespaceHandler 的初始化方法
				// 调用handler类的初始化方法(将命名空间下的节点名和对应的解析器注册到parsers缓存中)
				namespaceHandler.init();
				// 记录在缓存中
				// 将实例化的handler放到缓存，替换原来的className
				// 原来为: namespaceUri -> handler的className，会被覆盖成: namespaceUri -> 实例化的handler
				handlerMappings.put(namespaceUri, namespaceHandler);
				// 返回实例化后的handler对象
				return namespaceHandler;
			}
			catch (ClassNotFoundException ex) {
				throw new FatalBeanException("Could not find NamespaceHandler class [" + className +
						"] for namespace [" + namespaceUri + "]", ex);
			}
			catch (LinkageError err) {
				throw new FatalBeanException("Unresolvable class definition for NamespaceHandler class [" +
						className + "] for namespace [" + namespaceUri + "]", err);
			}
		}
	}

	/**
	 * Load the specified NamespaceHandler mappings lazily.
	 */
	private Map<String, Object> getHandlerMappings() {
		Map<String, Object> handlerMappings = this.handlerMappings;
		// 如果没有被缓存，则开始进行缓存
		if (handlerMappings == null) {
			synchronized (this) {
				handlerMappings = this.handlerMappings;
				if (handlerMappings == null) {
					if (logger.isTraceEnabled()) {
						logger.trace("Loading NamespaceHandler mappings from [" + this.handlerMappingsLocation + "]");
					}
					try {
						// 使用给定的类加载器从指定的类路径资源加载所有属性
						// this.handlerMappingsLocation 在构造函数中已经被初始化为 MATE-INF/spring.handlers
						//  handlerMappingsLocation 的默认值为 “META-INF/spring.handlers”，因此在这边会使用指定的 classLoader 从所
						//  有类路径资源（META-INF/spring.handlers）加载所有属性，并使用 Properties来存放 spring.handlers文件中的内容。
						Properties mappings =
								PropertiesLoaderUtils.loadAllProperties(this.handlerMappingsLocation, this.classLoader);
						if (logger.isTraceEnabled()) {
							logger.trace("Loaded NamespaceHandler mappings: " + mappings);
						}
						handlerMappings = new ConcurrentHashMap<>(mappings.size());
						// 将Properties转换成Map, mappings -> handlerMappings
						// 将 Properties 格式文件合并到 map 格式的 handlerMapping 中
						// 将 Properties转成 Map。其中 key 为命名空间，例如：http://www.springframework.org/schema/context；value 为命名空间对应的 handler，例如：org.springframework.context.config.ContextNamespaceHandler，所有的handler都需要自己实现
						CollectionUtils.mergePropertiesIntoMap(mappings, handlerMappings);
						// 将加载到的所有命名空间映射放到缓存
						this.handlerMappings = handlerMappings;
					}
					catch (IOException ex) {
						throw new IllegalStateException(
								"Unable to load NamespaceHandler mappings from location [" + this.handlerMappingsLocation + "]", ex);
					}
				}
			}
		}
		return handlerMappings;
	}


	@Override
	public String toString() {
		return "NamespaceHandlerResolver using mappings " + getHandlerMappings();
	}

}
