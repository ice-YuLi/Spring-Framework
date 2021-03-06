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

package org.springframework.aop.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.aop.Advisor;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.TargetClassAware;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Utility methods for AOP support code.
 *
 * <p>Mainly for internal use within Spring's AOP support.
 *
 * <p>See {@link org.springframework.aop.framework.AopProxyUtils} for a
 * collection of framework-specific AOP utility methods which depend
 * on internals of Spring's AOP framework implementation.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @see org.springframework.aop.framework.AopProxyUtils
 */
public abstract class AopUtils {

	/**
	 * Check whether the given object is a JDK dynamic proxy or a CGLIB proxy.
	 * <p>This method additionally checks if the given object is an instance
	 * of {@link SpringProxy}.
	 * @param object the object to check
	 * @see #isJdkDynamicProxy
	 * @see #isCglibProxy
	 */
	public static boolean isAopProxy(@Nullable Object object) {
		return (object instanceof SpringProxy &&
				(Proxy.isProxyClass(object.getClass()) || ClassUtils.isCglibProxyClass(object.getClass())));
	}

	/**
	 * Check whether the given object is a JDK dynamic proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link Proxy#isProxyClass(Class)} by additionally checking if the
	 * given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see java.lang.reflect.Proxy#isProxyClass
	 */
	public static boolean isJdkDynamicProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && Proxy.isProxyClass(object.getClass()));
	}

	/**
	 * Check whether the given object is a CGLIB proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link ClassUtils#isCglibProxy(Object)} by additionally checking if
	 * the given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see ClassUtils#isCglibProxy(Object)
	 */
	public static boolean isCglibProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && ClassUtils.isCglibProxy(object));
	}

	/**
	 * Determine the target class of the given bean instance which might be an AOP proxy.
	 * <p>Returns the target class for an AOP proxy or the plain class otherwise.
	 * @param candidate the instance to check (might be an AOP proxy)
	 * @return the target class (or the plain class of the given object as fallback;
	 * never {@code null})
	 * @see org.springframework.aop.TargetClassAware#getTargetClass()
	 * @see org.springframework.aop.framework.AopProxyUtils#ultimateTargetClass(Object)
	 */
	public static Class<?> getTargetClass(Object candidate) {
		Assert.notNull(candidate, "Candidate object must not be null");
		Class<?> result = null;
		if (candidate instanceof TargetClassAware) {
			result = ((TargetClassAware) candidate).getTargetClass();
		}
		if (result == null) {
			result = (isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass());
		}
		return result;
	}

	/**
	 * Select an invocable method on the target type: either the given method itself
	 * if actually exposed on the target type, or otherwise a corresponding method
	 * on one of the target type's interfaces or on the target type itself.
	 * @param method the method to check
	 * @param targetType the target type to search methods on (typically an AOP proxy)
	 * @return a corresponding invocable method on the target type
	 * @throws IllegalStateException if the given method is not invocable on the given
	 * target type (typically due to a proxy mismatch)
	 * @since 4.3
	 * @see MethodIntrospector#selectInvocableMethod(Method, Class)
	 */
	public static Method selectInvocableMethod(Method method, @Nullable Class<?> targetType) {
		if (targetType == null) {
			return method;
		}
		Method methodToUse = MethodIntrospector.selectInvocableMethod(method, targetType);
		if (Modifier.isPrivate(methodToUse.getModifiers()) && !Modifier.isStatic(methodToUse.getModifiers()) &&
				SpringProxy.class.isAssignableFrom(targetType)) {
			throw new IllegalStateException(String.format(
					"Need to invoke method '%s' found on proxy for target class '%s' but cannot " +
					"be delegated to target bean. Switch its visibility to package or protected.",
					method.getName(), method.getDeclaringClass().getSimpleName()));
		}
		return methodToUse;
	}

	/**
	 * Determine whether the given method is an "equals" method.
	 * @see java.lang.Object#equals
	 */
	public static boolean isEqualsMethod(@Nullable Method method) {
		return ReflectionUtils.isEqualsMethod(method);
	}

	/**
	 * Determine whether the given method is a "hashCode" method.
	 * @see java.lang.Object#hashCode
	 */
	public static boolean isHashCodeMethod(@Nullable Method method) {
		return ReflectionUtils.isHashCodeMethod(method);
	}

	/**
	 * Determine whether the given method is a "toString" method.
	 * @see java.lang.Object#toString()
	 */
	public static boolean isToStringMethod(@Nullable Method method) {
		return ReflectionUtils.isToStringMethod(method);
	}

	/**
	 * Determine whether the given method is a "finalize" method.
	 * @see java.lang.Object#finalize()
	 */
	public static boolean isFinalizeMethod(@Nullable Method method) {
		return (method != null && method.getName().equals("finalize") &&
				method.getParameterCount() == 0);
	}

	/**
	 * Given a method, which may come from an interface, and a target class used
	 * in the current AOP invocation, find the corresponding target method if there
	 * is one. E.g. the method may be {@code IFoo.bar()} and the target class
	 * may be {@code DefaultFoo}. In this case, the method may be
	 * {@code DefaultFoo.bar()}. This enables attributes on that method to be found.
	 * <p><b>NOTE:</b> In contrast to {@link org.springframework.util.ClassUtils#getMostSpecificMethod},
	 * this method resolves Java 5 bridge methods in order to retrieve attributes
	 * from the <i>original</i> method definition.
	 * @param method the method to be invoked, which may come from an interface
	 * @param targetClass the target class for the current invocation.
	 * May be {@code null} or may not even implement the method.
	 * @return the specific target method, or the original method if the
	 * {@code targetClass} doesn't implement it or is {@code null}
	 * @see org.springframework.util.ClassUtils#getMostSpecificMethod
	 */
	public static Method getMostSpecificMethod(Method method, @Nullable Class<?> targetClass) {
		Class<?> specificTargetClass = (targetClass != null ? ClassUtils.getUserClass(targetClass) : null);
		Method resolvedMethod = ClassUtils.getMostSpecificMethod(method, specificTargetClass);
		// If we are dealing with method with generic parameters, find the original method.
		return BridgeMethodResolver.findBridgedMethod(resolvedMethod);
	}

	/**
	 * Can the given pointcut apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize
	 * out a pointcut for a class.
	 * @param pc the static or dynamic pointcut to check
	 * @param targetClass the class to test
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass) {
		return canApply(pc, targetClass, false);
	}

	/**
	 * Can the given pointcut apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize
	 * out a pointcut for a class.
	 * @param pc the static or dynamic pointcut to check
	 * @param targetClass the class to test
	 * @param hasIntroductions whether or not the advisor chain
	 * for this bean includes any introductions
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass, boolean hasIntroductions) {
		Assert.notNull(pc, "Pointcut must not be null");

		// 获取当前 Advisor 的 ClassFilter，并且调用其 matches() 方法判断当前切点表达式是否与目标 bean 匹配，
		// 这里 ClassFilter 指代的切点表达式主要是当前切面类上使用的 @Aspect 注解中所指代的切点表达式
		if (!pc.getClassFilter().matches(targetClass)) {
			return false;
		}

		// 如果是事务执行，此时的 pc 表示 TransactionAttributeSourcePointcut
		// 判断如果当前 Advisor 所指代的方法的切点表达式如果是对任意方法都放行，则直接返回
		// (这里的 pc.getMethodMatcher() 中值 是从 上面 pc.getClassFilter()#obtainPointcutExpression() 方法赋值的)
		MethodMatcher methodMatcher = pc.getMethodMatcher();
		if (methodMatcher == MethodMatcher.TRUE) {
			// No need to iterate the methods if we're matching any method anyway...
			// 翻译：如果我们仍然要匹配任何方法，则无需迭代这些方法...
			return true;
		}

		// 这里将MethodMatcher强转为IntroductionAwareMethodMatcher类型的原因在于，
		// 如果目标类不包含Introduction类型的Advisor，那么使用
		// IntroductionAwareMethodMatcher.matches()方法进行匹配判断时可以提升匹配的效率，
		// 其会判断目标bean中没有使用Introduction织入新的方法，则可以使用该方法进行静态匹配，从而提升效率
		// 因为Introduction类型的Advisor可以往目标类中织入新的方法，新的方法也可能是被AOP环绕的方法
		IntroductionAwareMethodMatcher introductionAwareMethodMatcher = null;
		// methodMatcher 存储的是 AspectJExpressionPointcut 实体，AspectJExpressionPointcut 继承自 IntroductionAwareMethodMatcher 所
		// 以以下表达式成立
		if (methodMatcher instanceof IntroductionAwareMethodMatcher) {
			introductionAwareMethodMatcher = (IntroductionAwareMethodMatcher) methodMatcher;
		}

		Set<Class<?>> classes = new LinkedHashSet<>();
		if (!Proxy.isProxyClass(targetClass)) {
			classes.add(ClassUtils.getUserClass(targetClass));
		}
		// 获取目标类的所有接口
		classes.addAll(ClassUtils.getAllInterfacesForClassAsSet(targetClass));

		for (Class<?> clazz : classes) {
			// 获取目标接口的所有方法
			Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
			for (Method method : methods) {
				// 如果当前MethodMatcher也是IntroductionAwareMethodMatcher类型，则使用该类型
				// 的方法进行匹配，从而达到提升效率的目的；否则使用MethodMatcher.matches()方法进行匹配
				if (introductionAwareMethodMatcher != null ?
						introductionAwareMethodMatcher.matches(method, targetClass, hasIntroductions) :
						// 如果执行的是事务，会使 TransactionAttributeSourcePointcut 类的 matches 方法
						methodMatcher.matches(method, targetClass)) {
					return true;
				}
				// 通过上面的函数大致可以理清大体脉络，首先获取对应类的所有接口并连同类本身一起遍
				// 历，遍历过程中又对类中的方法再次遍历 ，一旦匹配成功便认为这个类适用于当前增强器。
			}
		}

		return false;

		// 在canApply()方法中，逻辑主要分为两个部分：通过ClassFilter对类进行过滤和通过MethodMatcher对方法进行过滤。这里
		// 的ClassFilter其实主要指的是@Aspect注解中使用的切点表达式，而MethodMatcher主要指的是@Before，@After等注解中
		// 使用的切点表达式。Spring Aop对切点表达式进行解析的过程都是通过递归来实现的，两种解析方式是类似的，这里我们主要讲解
		// Spring Aop是如何对方法上的切点表达式进行解析的，并且是如何匹配目标方法的
	}

	/**
	 * Can the given advisor apply at all on the given class?
	 * This is an important test as it can be used to optimize
	 * out a advisor for a class.
	 * @param advisor the advisor to check
	 * @param targetClass class we're testing
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass) {
		// see again
		return canApply(advisor, targetClass, false);
	}

	/**
	 * Can the given advisor apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize out a advisor for a class.
	 * This version also takes into account introductions (for IntroductionAwareMethodMatchers).
	 * @param advisor the advisor to check
	 * @param targetClass class we're testing
	 * @param hasIntroductions whether or not the advisor chain for this bean includes
	 * any introductions
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
		// see again
		if (advisor instanceof IntroductionAdvisor) {
			// 它只有ClassFilter，因为它只能作用在类层面上
			return ((IntroductionAdvisor) advisor).getClassFilter().matches(targetClass);
		}
		else if (advisor instanceof PointcutAdvisor) {
			PointcutAdvisor pca = (PointcutAdvisor) advisor;
			// canApply
			return canApply(pca.getPointcut(), targetClass, hasIntroductions);
		}
		else {
			// It doesn't have a pointcut so we assume it applies.
			// 它没有切入点，因此我们假设它适用。
			return true;
		}
	}

	/**
	 * Determine the sublist of the {@code candidateAdvisors} list
	 * that is applicable to the given class.
	 * @param candidateAdvisors the Advisors to evaluate
	 * @param clazz the target class
	 * @return sublist of Advisors that can apply to an object of the given class
	 * (may be the incoming List as-is)
	 */
	public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
		// findAdvisorsThatCanApply 函数的主要功能是找所有增强器中适用于当前 class 的增强器
		// 引介增强与普通增强处理的处理是不一样的所以分开处理,而对于真正的匹配在 canApply 中实现的(换种说法：该方法中始终会将Introduction类型的Advisor和其余的Advisor分开进行处理)
		if (candidateAdvisors.isEmpty()) {
			return candidateAdvisors;
		}
		List<Advisor> eligibleAdvisors = new ArrayList<>();
		// 首先处理引介增强 canApply
		// 首先处理引介增强（@DeclareParents）用的比较少可以忽略，有兴趣的参考：https://www.cnblogs.com/HigginCui/p/6322283.html
		// 判断当前Advisor是否为IntroductionAdvisor，如果是，则按照IntroductionAdvisor的方式进行过滤，这里主要的过滤逻辑在canApply()方法中
		for (Advisor candidate : candidateAdvisors) {
			// DeclareParents 继承自 IntroductionAdvisor
			// 判断是否为IntroductionAdvisor && 并且判断是否可以应用到当前类上
			if (candidate instanceof IntroductionAdvisor && canApply(candidate, clazz)) {
				eligibleAdvisors.add(candidate);
			}
		}
		boolean hasIntroductions = !eligibleAdvisors.isEmpty();
		for (Advisor candidate : candidateAdvisors) {
			// 引介增强已经处理
			if (candidate instanceof IntroductionAdvisor) {
				// already processed
				continue;
			}
			// 正常增强处理，判断当前bean是否可以应用于当前遍历的增强器（bean是否包含在增强器的execution指定的表达式中）
			// 主意前面的canApply()和下面这个参数个数不一样！
			if (canApply(candidate, clazz, hasIntroductions)) {
				eligibleAdvisors.add(candidate);
			}
		}
		return eligibleAdvisors;
	}

	/**
	 * Invoke the given target via reflection, as part of an AOP method invocation.
	 * @param target the target object
	 * @param method the method to invoke
	 * @param args the arguments for the method
	 * @return the invocation result, if any
	 * @throws Throwable if thrown by the target method
	 * @throws org.springframework.aop.AopInvocationException in case of a reflection error
	 */
	@Nullable
	public static Object invokeJoinpointUsingReflection(@Nullable Object target, Method method, Object[] args)
			throws Throwable {

		// Use reflection to invoke the method.
		try {
			ReflectionUtils.makeAccessible(method);
			return method.invoke(target, args);
		}
		catch (InvocationTargetException ex) {
			// Invoked method threw a checked exception.
			// We must rethrow it. The client won't see the interceptor.
			throw ex.getTargetException();
		}
		catch (IllegalArgumentException ex) {
			throw new AopInvocationException("AOP configuration seems to be invalid: tried calling method [" +
					method + "] on target [" + target + "]", ex);
		}
		catch (IllegalAccessException ex) {
			throw new AopInvocationException("Could not access method [" + method + "]", ex);
		}
	}

}
