/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Proxy;

import org.springframework.aop.SpringProxy;

/**
 * Default {@link AopProxyFactory} implementation, creating either a CGLIB proxy
 * or a JDK dynamic proxy.
 *
 * <p>Creates a CGLIB proxy if one the following is true for a given
 * {@link AdvisedSupport} instance:
 * <ul>
 * <li>the {@code optimize} flag is set
 * <li>the {@code proxyTargetClass} flag is set
 * <li>no proxy interfaces have been specified
 * </ul>
 *
 * <p>In general, specify {@code proxyTargetClass} to enforce a CGLIB proxy,
 * or specify one or more interfaces to use a JDK dynamic proxy.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 12.03.2004
 * @see AdvisedSupport#setOptimize
 * @see AdvisedSupport#setProxyTargetClass
 * @see AdvisedSupport#setInterfaces
 */
@SuppressWarnings("serial")
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {

	@Override
	public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
		// 到此已经完成了代理的创建，不管我之前是否阅读过 Spring 的源代码，但是都或多或少地
		// 听过对于 Spring 的代理中 JDKProxy 实现和 CglibProxy 的实现。 Spring 是如何选取的
		// 呢？网上的介绍有很多，现在我们就从源代码的角度分析，看看到底 Spring 如何选择代理方式的
		// 从 if 中的判断条件可以看到 3 个方面影响着 Spring 的判断
		// optimize：用来控制通过 CGLIB 创建的代理是否使用激进的优化策略，除非完全了解
		//			AOP 代理如何处理优化，否则不推荐用户使用这个设置，目前这个属性仅用 CGLIB
		//			代理，对于 JDK 动态代理（默认代理）无效
		// proxyTargetClass：这个属性为 true 时，目标类本身被代理而不是目标类接口。如果
		//					这个属性值被设为true, CGLIB 代理将被创建，设置方式
		//					为＜aop:aspectj-autoproxy-proxy-target-class = "true" ／＞
		// hasNoUserSuppliedProxyInterfaces：是否存在代理接口
		// 下面是对 JDK 与 Cglib 方式的总结。
		// 1、如果目标对象实现了接口，默认情况下会采用 JDK 的动态代理实现 AOP
		// 2、如果目标对象实现了接口，可以强制使用 CGLIB 实现 AOP
		// 3、如果目标对象没有实现接口，必须采用 CGLIB 库， Spring 会自动在 JDK 动态代理和 CGLIB 之间转换
		// 如何强制使用 CGLIB 实现 AOP
		// 1、添加 CGLIB 库， Spring_HOME/cglib/* .jar
		// 2、在 Spring 配置文件中加入＜aop:aspectj-autoproxy-proxy-target-class = "true" ／＞
		// JDK 动态代理和 CGLIB 字节码生成的区别？
		// 1、JDK 动态代理只能对实现了接口的类生成代理，而不能针对类
		// 2、CG LIB 是针对类实现代理，主要是对指定的类生成一个子类，覆盖其中的方法，因为是继承，所以
		//   该类或方法最好不要声明成 final
		// 优化（因为在早期 jdk 代理效率是不如 cglib 的）|| proxy-target-class 属性值 || proxyFactory.setInterfaces(UserInterface.class);属性是否设置
		if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
			// 获取设置的属性值 proxyFactory.setTargetClass(userService.getClass());
			Class<?> targetClass = config.getTargetClass();
			if (targetClass == null) {
				throw new AopConfigException("TargetSource cannot determine target class: " +
						"Either an interface or a target is required for proxy creation.");
			}
			//                            ||是不是 jdk 产生的代理类 ，如果是，还是使用 jdk 代理
			if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
				// JDKProxy
				return new JdkDynamicAopProxy(config);
			}
			// CglibProxy
			// CGLIB 是一个强大的高性能的代码生成包。它广泛地被许多 AOP 的框架使用，例如 Spring
			// AOP 和 dynaop，为它们提供方法的 Interception（拦截）。最流行的 OR Mapping 工具 Hibernate
			// 也使用 CGLIB 来代理单端 single-ended（多对一和一对一）关联（对集合的延迟抓取是采用其
			// 他机制实现的） EasyMock 和 jMock 是通过使用模仿（moke）对象来测试 Java 代码的包。它
			// 们都通过使用 CGLIB 来为那些没有接口的类创建模仿（moke）对象
			// CGLIB 包的底层通过使用一个小而快的字节码处理框架 ASM ，来转换字节码并生成新的类。
			// 除了 CGLIB 包，脚本语言例如 Groovy 和 BeanShell ，也是使用 ASM 来生成 Java 的字节
			// 码。当然不鼓励直接使用 ASM， 因为它要求你必须对 JVM 内部结构（包括 class 文件的格式
			// 和指令集） 都很熟悉
			return new ObjenesisCglibAopProxy(config);
		}
		else {
			return new JdkDynamicAopProxy(config);
		}
	}

	/**
	 * Determine whether the supplied {@link AdvisedSupport} has only the
	 * {@link org.springframework.aop.SpringProxy} interface specified
	 * (or no proxy interfaces specified at all).
	 */
	private boolean hasNoUserSuppliedProxyInterfaces(AdvisedSupport config) {
		Class<?>[] ifcs = config.getProxiedInterfaces();
		return (ifcs.length == 0 || (ifcs.length == 1 && SpringProxy.class.isAssignableFrom(ifcs[0])));
	}

}
