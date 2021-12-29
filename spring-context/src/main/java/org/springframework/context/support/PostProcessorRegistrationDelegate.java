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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		Set<String> processedBeans = new HashSet<>();

		// 对 BeanDefinitionRegistry 类型的处理
		// 判断 beanFactory 是否为 BeanDefinitionRegistry，beanFactory 为 DefaultListableBeanFactory,
		// 而 DefaultListableBeanFactory 实现了 BeanDefinitionRegistry 接口，因此这边为 true
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			// 用于存放普通的 BeanFactoryPostProcessor
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			// 用于存放 BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// 首先处理入参中的beanFactoryPostProcessors
			// 遍历所有的 beanFactoryPostProcessors , 将 BeanDefinitionRegistryPostProcessor 和普通 BeanFactoryPostProcessor 区分开
			// 通过 xxxxApplicationContext.addBeanFactoryPostProcessor(xxx) 硬编码注册的后处理器
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				// 添加后的后处理器会存放在 beanFactoryPostProcessors 中，而在处理 BeanFactoryPostProcessor
				// 时候会首先检测 beanFactoryPostProcessors 是否有数据。当然，BeanDefinitionRegistryPostProcessor
				// 继承自 BeanFactoryPostProcessor， 不但有 BeanFactoryPostProcessor 的特性， 同时还有自己定义
				// 的个性化方法，也需要在此调用。 所以，这里需要从 beanFactoryPostProcessors 中挑出
				// BeanDefinitionRegistryPostProcessor 的后处理器，并进行其 postProcessBeanDefinitionRegistry 的激活
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					// 对于 BeanDefinitionRegistryPostProcessor 类型，在 BeanFactoryPostProcessor 的基础
					// 上还有自己定义的 postProcessBeanDefinitionRegistry 方法，需要先于 postProcessBeanFactory 方法调用
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					// 添加到 registryProcessors (用于最后执行postProcessBeanFactory方法)
					registryProcessors.add(registryProcessor);
				}
				else {
					// 将普通的 BeanFactoryPostProcessor 添加到 regularPostProcessors(用于最后执行 postProcessBeanFactory 方法)
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 用于保存本次要执行的 BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 配置注册后的处理器
			// 找出所有实现BeanDefinitionRegistryPostProcessor接口的Bean的beanName
			// 调用所有实现PriorityOrdered接口的BeanDefinitionRegistryPostProcessor实现类

			// 这里获取的 BeanDefinitionRegistryPostProcessor 区别于上面的 BeanDefinitionRegistryPostProcessor ，上面
			// 的 BeanDefinitionRegistryPostProcessor 来源于 该流程之前自定义扩展的 Java 代码，而下面这个 BeanDefinitionRegistryPostProcessor

			// 如果使用 ClassPathXmlApplicationContext
			// 获取来源是 xml 配置文件中配置的实现了 BeanDefinitionRegistryPostProcessor 接口的类

			// 如果使用 AnnotationConfigApplicationContext
			// ConfigurationClassPostProcessor.class来自 AnnotationConfigUtils.registerAnnotationConfigProcessors() 方法注册进来的
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 校验是否实现了 PriorityOrdered 接口
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// 获取 ppName 对应的 bean 实例, 添加到 currentRegistryProcessors 中
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 将要被执行的加入 processedBeans，避免后续重复执行
					processedBeans.add(ppName);
				}
			}
			// 进行排序(根据是否实现 PriorityOrdered、Ordered 接口和 order 值来排序)
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 添加到 registryProcessors (用于最后执行 postProcessBeanFactory 方法)
			registryProcessors.addAll(currentRegistryProcessors);
			// 遍历 currentRegistryProcessors, 执行 postProcessBeanDefinitionRegistry 方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			// 执行完毕后, 清空currentRegistryProcessors
			currentRegistryProcessors.clear();
			// 上面操作的目的：调用所有实现 PriorityOrdered 接口的 BeanDefinitionRegistryPostProcessor 实现类的 postProcessBeanDefinitionRegistry 方法

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 找出所有实现 BeanDefinitionRegistryPostProcessor 接口的类, 这边重复查找是因为执行完上面的 BeanDefinitionRegistryPostProcessor,
			// 可能会新增了其他的 BeanDefinitionRegistryPostProcessor, 因此需要重新查找
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 校验是否实现了 Ordered 接口，并且还未执行过
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			// 最后, 调用所有剩下的 BeanDefinitionRegistryPostProcessors
			boolean reiterate = true;
			// 这里为什么会用 while 循环呢，是因为在调用了自定义的 postProcessBeanDefinitionRegistry 方法后，可能存在产生新的 BeanDefinitionRegistryPostProcessor的子
			// 类实现，所以这次循环检查一下，避免遗漏
			while (reiterate) {
				reiterate = false;
				// 找出所有实现 BeanDefinitionRegistryPostProcessor 接口的类
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					// 跳过已经执行过的
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						// 因此这边将 reiterate 赋值为 true, 代表需要再循环查找一次
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// 调用 postProcessBeanFactory 方法之前需要先调用 postProcessBeanDefinitionRegistry
			// 硬编码设置的 BeanDefinitionRegistryPostProcessor
			// 调用所有 BeanDefinitionRegistryPostProcessor 的 postProcessBeanFactory 方
			// 法(BeanDefinitionRegistryPostProcessor继承自BeanFactoryPostProcessor)
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			// 最后, 调用入参 beanFactoryPostProcessors 中的普通 BeanFactoryPostProcessor 的 postProcessBeanFactory 方法
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// 到这里 , 入参 beanFactoryPostProcessors 和容器中的所有 BeanDefinitionRegistryPostProcessor 已经全部处理完毕,
		// 下面开始处理非容器自己定义的 BeanFactoryPostProcessor

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 对配置中读取的 BeanFactoryPostProcessor 的处理
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 用于存放实现了PriorityOrdered接口的BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 用于存放实现了Ordered接口的BeanFactoryPostProcessor的beanName
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 用于存放普通BeanFactoryPostProcessor的beanName
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 对后处理器进行分类，遍历postProcessorNames, 将BeanFactoryPostProcessor按实现PriorityOrdered、实现Ordered接口、普通三种区分开
		for (String ppName : postProcessorNames) {
			// 跳过已经执行过的
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			// 添加实现了 PriorityOrdered 接口的 BeanFactoryPostProcessor
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			// 添加实现了 Ordered 接口的 BeanFactoryPostProcessor 的 beanName
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				// 添加剩下的普通 BeanFactoryPostProcessor 的 beanName
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 按照优先级顺序进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 遍历 priorityOrderedPostProcessors, 执行 postProcessBeanFactory 方法
		// 调用所有实现 PriorityOrdered 接口的 BeanFactoryPostProcessor
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : orderedPostProcessorNames) {
			// 获取 postProcessorName 对应的 bean 实例, 添加到 orderedPostProcessors, 准备执行
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 对 orderedPostProcessors 排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 遍历 orderedPostProcessors, 执行 postProcessBeanFactory 方法
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 无序直接调用
		// 调用所有剩下的 BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			// 获取 postProcessorName 对应的 bean 实例, 添加到 nonOrderedPostProcessors, 准备执行
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 遍历 nonOrderedPostProcessors, 执行 postProcessBeanFactory 方法
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		// 清除元数据缓存（mergedBeanDefinitions、allBeanNamesByType、singletonBeanNamesByType），
		// 因为后处理器可能已经修改了原始元数据，例如， 替换值中的占位符...
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		// 找出所有实现 BeanPostProcesso r接口的类
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.

		// BeanPostProcessor的目标计数
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		// BeanPostProcessorChecker 是一个普通的信息打印，可能会有些情况，
		// 当 Spring 的配置中的后处理器还没有被注册就已经开始了 bean 的初始化时
		// 便会打印 BeanPostProcessorChecker 中设定的信息
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.

		// 定义不同的变量用于区分:
		// 1. 实现 PriorityOrdered 接口的 BeanPostProcessor
		// 2. 实现Ordered接口的BeanPostProcessor
		// 3. 普通BeanPostProcessor

		// priorityOrderedPostProcessors: 用于存放实现 PriorityOrdered 接口的BeanPostProcessor
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// internalPostProcessors: 用于存放实现了 MergedBeanDefinitionPostProcessor 的 BeanPostProcessor
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		// orderedPostProcessorNames: 用于存放实现 Ordered 接口的 BeanPostProcessor 的 beanName
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// nonOrderedPostProcessorNames: 用于存放普通 BeanPostProcessor 的 beanName
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 遍历 postProcessorNames, 将 BeanPostProcessors 按实现的接口类型区分开
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// 如果 ppName 对应的 Bean 实例实现了 PriorityOrdered 接口, 则拿到 ppName 对应的 Bean 实例并添加到 priorityOrderedPostProcessors
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					// 如果 ppName 对应的 Bean 实例也实现了 MergedBeanDefinitionPostProcessor 接口,
					// 则将 ppName 对应的 Bean 实例添加到 internalPostProcessors
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				// 如果 ppName 对应的 Bean 实例没有实现 PriorityOrdered 接口, 但是实现了 Ordered 接口, 则将 ppName 添加到 orderedPostProcessorNames 中
				orderedPostProcessorNames.add(ppName);
			}
			else {
				// 否则, 将 ppName 添加到 nonOrderedPostProcessorNames
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 注册 priorityOrderedPostProcessors
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				// 如果 ppName 对应的 Bean 实例也实现了 MergedBeanDefinitionPostProcessor 接口,则
				// 将 ppName 对应的 Bean 实例添加到 internalPostProcessors 中
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		// 注册所有无序的、常规的 BeanPostProcessors
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		// 注册所有的 MergedBeanDefinitionPostProcessor 类型的 BeanPostProcessors，并非重复注册
		// 在 beanFactory.addBeanPostProcessor 中会先移除已经存在的 BeanPostProcess。
		// 最后, 重新注册所有实现了 MergedBeanDefinitionPostProcessor 的 BeanPostProcessors
		sortPostProcessors(internalPostProcessors, beanFactory);
		// 注册 internalPostProcessors
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		// 重新注册ApplicationListenerDetector（主要是为了移动到处理器链的末尾）
		// (第一次插入是在 org/springframework/context/support/AbstractApplicationContext.java:766)
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));

		// 总结：
		// beanPostProcessors 中的顺序：
		// 1 实现了 PriorityOrdered 接口，并且没有实现 MergedBeanDefinitionPostProcessor
		// 2 实现了 Order 接口，并且没有实现 MergedBeanDefinitionPostProcessor
		// 3 无序，并且没有实现 MergedBeanDefinitionPostProcessor
		// 4 实现了 MergedBeanDefinitionPostProcessor
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			// 获取设置的比较器
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			// 如果没有设置比较器, 则使用默认的 OrderComparator
			comparatorToUse = OrderComparator.INSTANCE;
		}
		// 使用比较器对 postProcessors 进行排序
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		// see again
		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			// ConfigurationClassPostProcessor#postProcessBeanDefinitionRegistry
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {
		for (BeanPostProcessor postProcessor : postProcessors) {
			// 将 PostProcessor 添加到 BeanFactory 中的 beanPostProcessors 缓存
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
