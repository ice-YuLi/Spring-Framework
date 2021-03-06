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

package org.springframework.aop.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Utility class for handling registration of AOP auto-proxy creators.
 *
 * <p>Only a single auto-proxy creator should be registered yet multiple concrete
 * implementations are available. This class provides a simple escalation protocol,
 * allowing a caller to request a particular auto-proxy creator and know that creator,
 * <i>or a more capable variant thereof</i>, will be registered as a post-processor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.5
 * @see AopNamespaceUtils
 */
public abstract class AopConfigUtils {

	/**
	 * The bean name of the internally managed auto-proxy creator.
	 */
	public static final String AUTO_PROXY_CREATOR_BEAN_NAME =
			"org.springframework.aop.config.internalAutoProxyCreator";

	/**
	 * Stores the auto proxy creator classes in escalation order.
	 */
	private static final List<Class<?>> APC_PRIORITY_LIST = new ArrayList<>(3);

	static {
		// Set up the escalation list...
		APC_PRIORITY_LIST.add(InfrastructureAdvisorAutoProxyCreator.class);
		APC_PRIORITY_LIST.add(AspectJAwareAdvisorAutoProxyCreator.class);
		APC_PRIORITY_LIST.add(AnnotationAwareAspectJAutoProxyCreator.class);
	}


	@Nullable
	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAutoProxyCreatorIfNecessary(registry, null);
	}

	@Nullable
	public static BeanDefinition registerAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		// 注册 InfrastructureAdvisorAutoProxyCreator 类型的 bean
		return registerOrEscalateApcAsRequired(InfrastructureAdvisorAutoProxyCreator.class, registry, source);

		// InfrastructureAdvisorAutoProxyCreator 间接实现了 SmartInstantiationAwareBeanPostProcessor
		// 而 SmartInstantiationAwareBeanPostProcessor 又继承 InstantiationAwareBeanPostProcessor ，也
		// 就是说在 Spring 中，所有 bean 实例化时 Spring 都会保证调用其 postProcessAfterInitialization 方法
		//，其实现是在父类 AbstractAutoProxyCreator 类中实现的。
	}

	@Nullable
	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAutoProxyCreatorIfNecessary(registry, null);
	}

	@Nullable
	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		return registerOrEscalateApcAsRequired(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
	}

	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry, null);
	}

	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			BeanDefinitionRegistry registry, @Nullable Object source) {

		// 注册或升级 AnnotationAwareAspectJAutoProxyCreator
		return registerOrEscalateApcAsRequired(AnnotationAwareAspectJAutoProxyCreator.class, registry, source);
	}

	public static void forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry registry) {
		// 强制使用的过程，其实也是一个属性设置的过程
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			definition.getPropertyValues().add("proxyTargetClass", Boolean.TRUE);
		}
		// proxy-target-class: Spring AOP 部分使用 JDK 动态代理或者 CGLIB 来为目标对象创建
		// 代理（建议尽量使用 JDK 的动态代理） 如果被代理的目标对象实现了至少一个接口，
		// 则会使用 JDK 动态代理 所有该目标类型实现的接口都将被代理，若该目标对象没有
		// 实现任何接口，则创建一个 CGLIB 代理 如果你希望强制使用 CGLIB 代理（例如希
		// 望代理目标对象的所有方法，而不只是实现自接口的方法），那也可以 但是需妥考虑
		// 以下两个问题
		// 1、无法通知（advise) Final 方法，因为它们不能被覆盖
		// 2、你需要将 CGLIB 二进制友行包放在 classpath 下面
		// 与之相比 JDK 本身就提供了动态代理,,强制使用 CGLIB 代理需要 <aop:config＞的
		// proxy-target-class 属性设为 true:
		// <aop:config proxy-target-class="true"></aop:config>
		// 当需要使用 CGLIB 代理和 @AspectJ 自动代理支持，可以按照以下方式设置＜aop:aspectj-autoproxy> 的 proxy-target class 属性：
		// <aop:aspectj-autoproxy proxy-target-class="true"/>
		// 而实际使用的过程中才会发现细节问题的差别：
		// 1、JDK 动态代理：其代理对象必须是某个接口的实现，它是通过在运行期间创建一个接口的实现类，来完成对目标对象的代理
		// 2、CGLIB 代理：实现原理类似于 JDK 动态代理，只是它在运行期间生成的代理对象是针对目标类扩展的子类 CGLIB 是高效
		// 的代码生成包，底层是依靠 ASM （开源的 Java 字节码编辑类库）操作字节码实现的，性能比 JDK 强
		// 3、expose-proxy ：有时候目标对象内部的自我调用将无法实施切面中的增强，如下示例：
		// 略 见 188 页
		// 此处的 this 指向目标对象，因此调用 this.b() 将不会执行 b 事务切面，即不会执行事务增强，
		// 因此 b 方法的事务定义 "@Transactional(propagation = Propagation.REQUIRES_NEW)" 将不会
		// 实施，为了解决这个问题，我们可以这样做
		// <aop:aspectj-autoproxy expose-proxy= "true" />
		// 然后将以上代码中的 "this.b();"修改为 ((AService) AopContext.currentProxy()).b();" 即可。
		// 通过以上的修改便可以完成对 a 和 b 方法的同时增强
		// 最后注册组件并通知，便于监听器做进一步处理，这里就不再一一赘述了。

	}

	public static void forceAutoProxyCreatorToExposeProxy(BeanDefinitionRegistry registry) {
		// 强制使用的过程，其实也是一个属性设置的过程
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			definition.getPropertyValues().add("exposeProxy", Boolean.TRUE);
		}
	}

	@Nullable
	private static BeanDefinition registerOrEscalateApcAsRequired(
			Class<?> cls, BeanDefinitionRegistry registry, @Nullable Object source) {

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

		// 如果注册表中已经存在beanName=org.springframework.aop.config.internalAutoProxyCreator的bean，则按优先级进行选择。
		// beanName=org.springframework.aop.config.internalAutoProxyCreator，可能存在的beanClass有三种，按优先级排序如下：
		// InfrastructureAdvisorAutoProxyCreator、AspectJAwareAdvisorAutoProxyCreator、AnnotationAwareAspectJAutoProxyCreator
		// 如果已经存在了自动代理创建器且存在的自动代理创建器与现在的不一致，那么需要根据优先级来判断到底需要使用哪个
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			// 拿到已经存在的bean定义
			BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			// 如果已经存在的bean的className与当前要注册的bean的className不相同，则按优先级进行选择
			if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
				// 拿到已经存在的bean的优先级
				int currentPriority = findPriorityForClass(apcDefinition.getBeanClassName());
				// 拿到当前要注册的bean的优先级
				int requiredPriority = findPriorityForClass(cls);
				if (currentPriority < requiredPriority) {
					// 如果当前要注册的bean的优先级大于已经存在的bean的优先级，则将bean的className替换为当前要注册的bean的className，
					// 改变 bean 最重要的就是改变 bean 所对应的 className 属性
					apcDefinition.setBeanClassName(cls.getName());
				}
				// 如果小于，则不做处理
			}
			// 如果已经存在的bean的className与当前要注册的bean的className相同，则无需进行任何处理
			return null;
		}

		// 如果注册表中还不存在，则新建一个Bean定义，并添加到注册表中
		RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
		beanDefinition.setSource(source);
		// 设置了order为最高优先级,数越小优先级越高
		beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		//  注册BeanDefinition，beanName为org.springframework.aop.config.internalAutoProxyCreator
		registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME, beanDefinition);
		return beanDefinition;

		// 参考链接：https://blog.csdn.net/v123411739/article/details/103544053
		// org.springframework.aop.config.internalAutoProxyCreator是内部管理的自动代理创建者的 bean 名称，
		// 可能对应的beanClassName有三种，对应的注解如下：
		// InfrastructureAdvisorAutoProxyCreator：<tx:annotation-driven />
		// AspectJAwareAdvisorAutoProxyCreator：<aop:config />
		// AnnotationAwareAspectJAutoProxyCreator：<aop:aspectj-autoproxy />
		// 当同时存在多个注解时，会使用优先级最高的beanClassName来作为org.springframework.aop.config.internalAutoProxyCreator
		// 的beanClassName。
	}

	private static int findPriorityForClass(Class<?> clazz) {
		return APC_PRIORITY_LIST.indexOf(clazz);
	}

	private static int findPriorityForClass(@Nullable String className) {
		for (int i = 0; i < APC_PRIORITY_LIST.size(); i++) {
			Class<?> clazz = APC_PRIORITY_LIST.get(i);
			if (clazz.getName().equals(className)) {
				return i;
			}
		}
		throw new IllegalArgumentException(
				"Class name [" + className + "] is not a known auto-proxy creator class");
	}

}
