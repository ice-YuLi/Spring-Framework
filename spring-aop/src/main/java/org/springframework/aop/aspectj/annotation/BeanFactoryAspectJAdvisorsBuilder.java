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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	@Nullable
	private volatile List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * Look for AspectJ-annotated aspect beans in the current bean factory,
	 * and return to a list of Spring AOP Advisors representing them.
	 * <p>Creates a Spring Advisor for each AspectJ advice method.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors() {
		List<String> aspectNames = this.aspectBeanNames;

		// 如果aspectNames为空，则进行解析
		if (aspectNames == null) {
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				// 第一次初始化，synchronized加双次判断，和经典单例模式的写法一样。
				if (aspectNames == null) {
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();
					// 获取 beanFactory 中所有的 beanName
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);
					// 循环所有的 beanName 找出对应的增强方法
					for (String beanName : beanNames) {
						// 不合法的beanName则跳过，默认返回true，子类可以覆盖实现，AnnotationAwareAspectJAutoProxyCreator
						// 实现了自己的逻辑，支持使用includePatterns进行筛选
						if (!isEligibleBean(beanName)) {
							continue;
						}
						// We must be careful not to instantiate beans eagerly as in this case they
						// would be cached by the Spring container but would not have been weaved.
						// 获取beanName对应的bean的类型
						Class<?> beanType = this.beanFactory.getType(beanName);
						if (beanType == null) {
							continue;
						}
						// 如果beanType存在Aspect注解则进行处理
						if (this.advisorFactory.isAspect(beanType)) {
							// 将存在Aspect注解的beanName添加到aspectNames列表
							aspectNames.add(beanName);
							// 对于使用了@Aspect注解标注的bean，将其封装为一个AspectMetadata类型。
							// 这里在封装的过程中会解析@Aspect注解上的参数指定的切面类型，如 perthis
							// 和 pertarget 等。这些被解析的注解都会被封装到其perClausePointcut属性中
							AspectMetadata amd = new AspectMetadata(beanType, beanName);
							// 判断 @Aspect 注解中标注的是否为singleton类型，默认的切面类都是 singleton 类型
							// 获取 getPerClause() 的类型是SINGLETON
							if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
								// 使用 BeanFactory 和 beanName 创建一个BeanFactoryAspectInstanceFactory，主要用来创建切面对象实例
								// 这里会再次将 @Aspect 注解中的参数都封装为一个AspectMetadata，并且保存在该factory中
								MetadataAwareAspectInstanceFactory factory =
										new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
								// 解析标记 AspectJ 注解中的增强方法
								// 通过封装的bean获取其Advice，如@Before，@After等等，并且将这些
								// Advice都解析并且封装为一个个的Advisor
								List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
								// 放到缓存中
								if (this.beanFactory.isSingleton(beanName)) {
									// 如果beanName是单例则直接将解析的增强方法放到缓存
									this.advisorsCache.put(beanName, classAdvisors);
								}
								else {
									// 如果不是单例，则将factory放到缓存，之后可以通过factory来解析增强方法
									this.aspectFactoryCache.put(beanName, factory);
								}
								// 将解析的增强器添加到advisors
								advisors.addAll(classAdvisors);
							}
							else {
								// Per target or per this.
								// 如果 getPerClause() 的类型不是SINGLETON
								// 如果 @Aspect 注解标注的是 perthis 和 pertarget 类型，说明当前切面
								// 不可能是单例的，因而这里判断其如果是单例的则抛出异常
								if (this.beanFactory.isSingleton(beanName)) {
									// 名称为beanName的Bean是单例，但切面实例化模型不是单例，则抛异常
									throw new IllegalArgumentException("Bean with name '" + beanName +
											"' is a singleton, but aspect instantiation model is not singleton");
								}
								MetadataAwareAspectInstanceFactory factory =
										new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
								// 将factory放到缓存，之后可以通过factory来解析增强方法
								this.aspectFactoryCache.put(beanName, factory);
								// 解析标记AspectJ注解中的增强方法，并添加到advisors中
								advisors.addAll(this.advisorFactory.getAdvisors(factory));
							}
						}
					}
					// 将解析出来的切面beanName放到缓存aspectBeanNames
					this.aspectBeanNames = aspectNames;
					// 最后返回解析出来的增强器
					return advisors;
				}
			}
		}

		// 如果aspectNames是空列表，则返回一个空列表。空列表也是解析过的，只要不是null都是解析过的
		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}
		// 如果 aspectNames 不为 null，则代表已经解析过了，则无需再次解析
		// aspectNames 不是空列表，则遍历处理
		List<Advisor> advisors = new ArrayList<>();
		for (String aspectName : aspectNames) {
			// 根据 aspectName 从缓存中获取增强器
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			if (cachedAdvisors != null) {
				// 根据上面的解析，可以知道 advisorsCache 存的是已经解析好的增强器，直接添加到结果即可
				advisors.addAll(cachedAdvisors);
			}
			else {
				// 如果不存在于 advisorsCache 缓存，则代表存在于 aspectFactoryCache 中，
				// 从 aspectFactoryCache 中拿到缓存的 factory，然后解析出增强器，添加到结果中
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				// 解析标记 AspectJ 注解中的增强方法
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		// 返回增强器
		return advisors;

		// 对于通过@Aspect注解获取切面逻辑的方法，这里的逻辑也比较简单，Spring首先会过滤得到BeanFactory中所有标注有@Aspect的
		// 类，然后对该注解参数进行解析，判断其环绕的目标bean是单例的还是多例的。如果是单例的，则直接缓存到advisorsCache中；如果
		// 是多例的，则将生成Advisor的factory进行缓存，以便每次获取时都通过factory获取一个新的Advisor。上述方法中主要是对@Aspect注
		// 解进行了解析.Spring Aop的Advisor对应的是Advice，而每个Advice都是对应的一个@Before或者@After等标
		// 注方法的切面逻辑，这里对这些切面逻辑的解析过程就在上述的advisorFactory.getAdvisors(factory)方法调用中。
	}

	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
