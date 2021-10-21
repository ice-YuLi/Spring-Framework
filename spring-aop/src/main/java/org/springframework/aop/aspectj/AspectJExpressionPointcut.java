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

package org.springframework.aop.aspectj;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.weaver.patterns.NamePattern;
import org.aspectj.weaver.reflect.ReflectionWorld.ReflectionWorldException;
import org.aspectj.weaver.reflect.ShadowMatchImpl;
import org.aspectj.weaver.tools.ContextBasedMatcher;
import org.aspectj.weaver.tools.FuzzyBoolean;
import org.aspectj.weaver.tools.JoinPointMatch;
import org.aspectj.weaver.tools.MatchingContext;
import org.aspectj.weaver.tools.PointcutDesignatorHandler;
import org.aspectj.weaver.tools.PointcutExpression;
import org.aspectj.weaver.tools.PointcutParameter;
import org.aspectj.weaver.tools.PointcutParser;
import org.aspectj.weaver.tools.PointcutPrimitive;
import org.aspectj.weaver.tools.ShadowMatch;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.framework.autoproxy.ProxyCreationContext;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.aop.support.AbstractExpressionPointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Spring {@link org.springframework.aop.Pointcut} implementation
 * that uses the AspectJ weaver to evaluate a pointcut expression.
 *
 * <p>The pointcut expression value is an AspectJ expression. This can
 * reference other pointcuts and use composition and other operations.
 *
 * <p>Naturally, as this is to be processed by Spring AOP's proxy-based model,
 * only method execution pointcuts are supported.
 *
 * @author Rob Harrop
 * @author Adrian Colyer
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Dave Syer
 * @since 2.0
 */
@SuppressWarnings("serial")
public class AspectJExpressionPointcut extends AbstractExpressionPointcut
		implements ClassFilter, IntroductionAwareMethodMatcher, BeanFactoryAware {

	private static final Set<PointcutPrimitive> SUPPORTED_PRIMITIVES = new HashSet<>();

	static {
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.EXECUTION);
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.ARGS);
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.REFERENCE);
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.THIS);
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.TARGET);
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.WITHIN);
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_ANNOTATION);
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_WITHIN);
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_ARGS);
		SUPPORTED_PRIMITIVES.add(PointcutPrimitive.AT_TARGET);
	}


	private static final Log logger = LogFactory.getLog(AspectJExpressionPointcut.class);

	@Nullable
	private Class<?> pointcutDeclarationScope;

	private String[] pointcutParameterNames = new String[0];

	private Class<?>[] pointcutParameterTypes = new Class<?>[0];

	@Nullable
	private BeanFactory beanFactory;

	@Nullable
	private transient ClassLoader pointcutClassLoader;

	@Nullable
	private transient PointcutExpression pointcutExpression;

	private transient Map<Method, ShadowMatch> shadowMatchCache = new ConcurrentHashMap<>(32);


	/**
	 * Create a new default AspectJExpressionPointcut.
	 */
	public AspectJExpressionPointcut() {
	}

	/**
	 * Create a new AspectJExpressionPointcut with the given settings.
	 * @param declarationScope the declaration scope for the pointcut
	 * @param paramNames the parameter names for the pointcut
	 * @param paramTypes the parameter types for the pointcut
	 */
	public AspectJExpressionPointcut(Class<?> declarationScope, String[] paramNames, Class<?>[] paramTypes) {
		this.pointcutDeclarationScope = declarationScope;
		if (paramNames.length != paramTypes.length) {
			throw new IllegalStateException(
					"Number of pointcut parameter names must match number of pointcut parameter types");
		}
		this.pointcutParameterNames = paramNames;
		this.pointcutParameterTypes = paramTypes;
	}


	/**
	 * Set the declaration scope for the pointcut.
	 */
	public void setPointcutDeclarationScope(Class<?> pointcutDeclarationScope) {
		this.pointcutDeclarationScope = pointcutDeclarationScope;
	}

	/**
	 * Set the parameter names for the pointcut.
	 */
	public void setParameterNames(String... names) {
		this.pointcutParameterNames = names;
	}

	/**
	 * Set the parameter types for the pointcut.
	 */
	public void setParameterTypes(Class<?>... types) {
		this.pointcutParameterTypes = types;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public ClassFilter getClassFilter() {
		obtainPointcutExpression();
		return this;
	}

	@Override
	public MethodMatcher getMethodMatcher() {
		obtainPointcutExpression();
		return this;
	}


	/**
	 * Check whether this pointcut is ready to match,
	 * lazily building the underlying AspectJ pointcut expression.
	 */
	private PointcutExpression obtainPointcutExpression() {
		// 如果切点表达式为空，则抛出异常
		if (getExpression() == null) {
			throw new IllegalStateException("Must set property 'expression' before attempting to match");
		}
		if (this.pointcutExpression == null) {
			// 获取切点表达式类加载器，默认和Spring使用的类加载器是同一加载器
			this.pointcutClassLoader = determinePointcutClassLoader();
			// 对切点表达式进行解析
			this.pointcutExpression = buildPointcutExpression(this.pointcutClassLoader);
		}
		return this.pointcutExpression;
	}

	/**
	 * Determine the ClassLoader to use for pointcut evaluation.
	 */
	@Nullable
	private ClassLoader determinePointcutClassLoader() {
		if (this.beanFactory instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) this.beanFactory).getBeanClassLoader();
		}
		if (this.pointcutDeclarationScope != null) {
			return this.pointcutDeclarationScope.getClassLoader();
		}
		return ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Build the underlying AspectJ pointcut expression.
	 */
	private PointcutExpression buildPointcutExpression(@Nullable ClassLoader classLoader) {
		// 使用类加载器实例化一个PointcutParser对象，用于对切点表达式进行解析
		PointcutParser parser = initializePointcutParser(classLoader);
		// 将切点表达式中使用args属性指定的参数封装为PointcutParameter类型的对象
		PointcutParameter[] pointcutParameters = new PointcutParameter[this.pointcutParameterNames.length];
		for (int i = 0; i < pointcutParameters.length; i++) {
			pointcutParameters[i] = parser.createPointcutParameter(
					this.pointcutParameterNames[i], this.pointcutParameterTypes[i]);
		}

		// https://my.oschina.net/zhangxufeng/blog/1930106
		// https://my.oschina.net/zhangxufeng/blog/1930278
		// buildPointcutExpression()方法首先实例化了一个PointcutParser，然后将@Before，@After注解中args属性指定的参数进
		// 行了封装，最后通过PointcutParser对切点表达式进行解析,如下是PointcutParser.parsePointcutExpression()的源码

		// 使用PointcutParser对切点表达式进行转化，这里replaceBooleanOperators()只是做了一个简单的
		// 字符串转换，将and、or和not转换为&&、||和!
		return parser.parsePointcutExpression(replaceBooleanOperators(resolveExpression()),
				this.pointcutDeclarationScope, pointcutParameters);
	}

	// https://my.oschina.net/zhangxufeng/blog/1930278
//	public PointcutExpression parsePointcutExpression(String expression, Class<?> inScope,
//													  PointcutParameter[] formalParameters)
//			throws UnsupportedPointcutPrimitiveException, IllegalArgumentException {
//		PointcutExpressionImpl pcExpr = null;
//		try {
//			// 对切点表达式进行解析
//			Pointcut pc = resolvePointcutExpression(expression, inScope, formalParameters);
//			pc = concretizePointcutExpression(pc, inScope, formalParameters);
//			// 对切点表达式执行的类型进行校验
//			validateAgainstSupportedPrimitives(pc, expression);
//			// 将解析得到的Pointcut封装到PointcutExpression中
//			pcExpr = new PointcutExpressionImpl(pc, expression, formalParameters, getWorld());
//		} catch (ParserException pEx) {
//			throw new IllegalArgumentException(
//					buildUserMessageFromParserException(expression, pEx));
//		} catch (ReflectionWorld.ReflectionWorldException rwEx) {
//			throw new IllegalArgumentException(rwEx.getMessage());
//		}
//		return pcExpr;
//	}

//	protected Pointcut resolvePointcutExpression(String expression, Class<?> inScope,
//												 PointcutParameter[] formalParameters) {
//		try {
//			// 将切点表达式封装到PatternParser中
//			PatternParser parser = new PatternParser(expression);
//			// 设置自定义的切点表达式处理器
//			parser.setPointcutDesignatorHandlers(pointcutDesignators, world);
//			// 解析切点表达式
//			Pointcut pc = parser.parsePointcut();
//			// 校验切点表达式是否为支持的类型
//			validateAgainstSupportedPrimitives(pc, expression);
//			// 将args属性所指定的参数封装到IScope中
//			IScope resolutionScope = buildResolutionScope((inScope == null
//					? Object.class : inScope), formalParameters);
//			// 通过args属性指定的参数与当前切面方法的参数进行对比，并且将方法的参数类型封装到Pointcut中
//			pc = pc.resolve(resolutionScope);
//			return pc;
//		} catch (ParserException pEx) {
//			throw new IllegalArgumentException(buildUserMessageFromParserException(expression, pEx));
//		}
//	}

	private String resolveExpression() {
		String expression = getExpression();
		Assert.state(expression != null, "No expression set");
		return expression;
	}

	/**
	 * Initialize the underlying AspectJ pointcut parser.
	 */
	private PointcutParser initializePointcutParser(@Nullable ClassLoader classLoader) {
		PointcutParser parser = PointcutParser
				.getPointcutParserSupportingSpecifiedPrimitivesAndUsingSpecifiedClassLoaderForResolution(
						SUPPORTED_PRIMITIVES, classLoader);
		parser.registerPointcutDesignatorHandler(new BeanPointcutDesignatorHandler());
		return parser;
	}


	/**
	 * If a pointcut expression has been specified in XML, the user cannot
	 * write {@code and} as "&&" (though &amp;&amp; will work).
	 * We also allow {@code and} between two pointcut sub-expressions.
	 * <p>This method converts back to {@code &&} for the AspectJ pointcut parser.
	 */
	private String replaceBooleanOperators(String pcExpr) {
		String result = StringUtils.replace(pcExpr, " and ", " && ");
		result = StringUtils.replace(result, " or ", " || ");
		result = StringUtils.replace(result, " not ", " ! ");
		return result;
	}


	/**
	 * Return the underlying AspectJ pointcut expression.
	 */
	public PointcutExpression getPointcutExpression() {
		return obtainPointcutExpression();
	}

	@Override
	public boolean matches(Class<?> targetClass) {
		PointcutExpression pointcutExpression = obtainPointcutExpression();
		try {
			try {
				return pointcutExpression.couldMatchJoinPointsInType(targetClass);
			}
			catch (ReflectionWorldException ex) {
				logger.debug("PointcutExpression matching rejected target class - trying fallback expression", ex);
				// Actually this is still a "maybe" - treat the pointcut as dynamic if we don't know enough yet
				PointcutExpression fallbackExpression = getFallbackPointcutExpression(targetClass);
				if (fallbackExpression != null) {
					return fallbackExpression.couldMatchJoinPointsInType(targetClass);
				}
			}
		}
		catch (Throwable ex) {
			logger.debug("PointcutExpression matching rejected target class", ex);
		}
		return false;
	}

	@Override
	public boolean matches(Method method, Class<?> targetClass, boolean hasIntroductions) {
		// 其主要做了两件事：对切点表达式进行解析，和通过解析的切点表达式与目标方法进行匹配。

		// 获取切点表达式，并对其进行解析，解析之后将解析的结果进行缓存
		obtainPointcutExpression();
		// 获取目标方法最接近的方法，比如如果method是接口方法，那么就找到该接口方法的实现类的方法
		// 关于切点的匹配，这里主要是在getShadowMatch()方法中实现的
		ShadowMatch shadowMatch = getTargetShadowMatch(method, targetClass);

		// Special handling for this, target, @this, @target, @annotation
		// in Spring - we can optimize since we know we have exactly this class,
		// and there will never be matching subclass at runtime.
		// 将对切点表达式解析后的结果与要匹配的目标方法封装为一个 ShadowMatch 对象，并且对目标方法进行
		// 匹配，匹配的结果将存储在 ShadowMatch.match 参数中，该参数是 FuzzyBoolean 类型的，
		// 其保存了当前方法与切点表达式的匹配结果
		if (shadowMatch.alwaysMatches()) {
			// 如果匹配上了则返回 true
			return true;
		}
		else if (shadowMatch.neverMatches()) {
			// 如果没匹配上则返回 false
			return false;
		}
		else {
			// the maybe case
			// 在不确认能否匹配的时候，通过判断是否有Introduction类型的Advisor，来进行进一步的匹配
			if (hasIntroductions) {
				return true;
			}
			// A match test returned maybe - if there are any subtype sensitive variables
			// involved in the test (this, target, at_this, at_target, at_annotation) then
			// we say this is not a match as in Spring there will never be a different
			// runtime subtype.
			// 如果不确认能否匹配，则将匹配结果封装为一个RuntimeTestWalker，
			// 以便在方法运行时进行动态匹配
			RuntimeTestWalker walker = getRuntimeTestWalker(shadowMatch);
			return (!walker.testsSubtypeSensitiveVars() || walker.testTargetInstanceOfResidue(targetClass));
		}
	}

	@Override
	public boolean matches(Method method, Class<?> targetClass) {
		return matches(method, targetClass, false);
	}

	@Override
	public boolean isRuntime() {
		return obtainPointcutExpression().mayNeedDynamicTest();
	}

	@Override
	public boolean matches(Method method, Class<?> targetClass, Object... args) {
		obtainPointcutExpression();
		ShadowMatch shadowMatch = getTargetShadowMatch(method, targetClass);

		// Bind Spring AOP proxy to AspectJ "this" and Spring AOP target to AspectJ target,
		// consistent with return of MethodInvocationProceedingJoinPoint
		ProxyMethodInvocation pmi = null;
		Object targetObject = null;
		Object thisObject = null;
		try {
			MethodInvocation mi = ExposeInvocationInterceptor.currentInvocation();
			targetObject = mi.getThis();
			if (!(mi instanceof ProxyMethodInvocation)) {
				throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
			}
			pmi = (ProxyMethodInvocation) mi;
			thisObject = pmi.getProxy();
		}
		catch (IllegalStateException ex) {
			// No current invocation...
			if (logger.isDebugEnabled()) {
				logger.debug("Could not access current invocation - matching with limited context: " + ex);
			}
		}

		try {
			JoinPointMatch joinPointMatch = shadowMatch.matchesJoinPoint(thisObject, targetObject, args);

			/*
			 * Do a final check to see if any this(TYPE) kind of residue match. For
			 * this purpose, we use the original method's (proxy method's) shadow to
			 * ensure that 'this' is correctly checked against. Without this check,
			 * we get incorrect match on this(TYPE) where TYPE matches the target
			 * type but not 'this' (as would be the case of JDK dynamic proxies).
			 * <p>See SPR-2979 for the original bug.
			 */
			if (pmi != null && thisObject != null) {  // there is a current invocation
				RuntimeTestWalker originalMethodResidueTest = getRuntimeTestWalker(getShadowMatch(method, method));
				if (!originalMethodResidueTest.testThisInstanceOfResidue(thisObject.getClass())) {
					return false;
				}
				if (joinPointMatch.matches()) {
					bindParameters(pmi, joinPointMatch);
				}
			}

			return joinPointMatch.matches();
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to evaluate join point for arguments " + Arrays.asList(args) +
						" - falling back to non-match", ex);
			}
			return false;
		}
	}

	@Nullable
	protected String getCurrentProxiedBeanName() {
		return ProxyCreationContext.getCurrentProxiedBeanName();
	}


	/**
	 * Get a new pointcut expression based on a target class's loader rather than the default.
	 */
	@Nullable
	private PointcutExpression getFallbackPointcutExpression(Class<?> targetClass) {
		try {
			ClassLoader classLoader = targetClass.getClassLoader();
			if (classLoader != null && classLoader != this.pointcutClassLoader) {
				return buildPointcutExpression(classLoader);
			}
		}
		catch (Throwable ex) {
			logger.debug("Failed to create fallback PointcutExpression", ex);
		}
		return null;
	}

	private RuntimeTestWalker getRuntimeTestWalker(ShadowMatch shadowMatch) {
		if (shadowMatch instanceof DefensiveShadowMatch) {
			return new RuntimeTestWalker(((DefensiveShadowMatch) shadowMatch).primary);
		}
		return new RuntimeTestWalker(shadowMatch);
	}

	private void bindParameters(ProxyMethodInvocation invocation, JoinPointMatch jpm) {
		// Note: Can't use JoinPointMatch.getClass().getName() as the key, since
		// Spring AOP does all the matching at a join point, and then all the invocations
		// under this scenario, if we just use JoinPointMatch as the key, then
		// 'last man wins' which is not what we want at all.
		// Using the expression is guaranteed to be safe, since 2 identical expressions
		// are guaranteed to bind in exactly the same way.
		invocation.setUserAttribute(resolveExpression(), jpm);
	}

	private ShadowMatch getTargetShadowMatch(Method method, Class<?> targetClass) {
		Method targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
		if (targetMethod.getDeclaringClass().isInterface()) {
			// Try to build the most specific interface possible for inherited methods to be
			// considered for sub-interface matches as well, in particular for proxy classes.
			// Note: AspectJ is only going to take Method.getDeclaringClass() into account.
			Set<Class<?>> ifcs = ClassUtils.getAllInterfacesForClassAsSet(targetClass);
			if (ifcs.size() > 1) {
				try {
					Class<?> compositeInterface = ClassUtils.createCompositeInterface(
							ClassUtils.toClassArray(ifcs), targetClass.getClassLoader());
					targetMethod = ClassUtils.getMostSpecificMethod(targetMethod, compositeInterface);
				}
				catch (IllegalArgumentException ex) {
					// Implemented interfaces probably expose conflicting method signatures...
					// Proceed with original target method.
				}
			}
		}
		return getShadowMatch(targetMethod, method);
	}

	private ShadowMatch getShadowMatch(Method targetMethod, Method originalMethod) {
		// Avoid lock contention for known Methods through concurrent access...
		// 从缓存中获取ShadowMatch数据，如果缓存中存在则直接返回
		ShadowMatch shadowMatch = this.shadowMatchCache.get(targetMethod);
		if (shadowMatch == null) {
			synchronized (this.shadowMatchCache) {
				// Not found - now check again with full lock...
				PointcutExpression fallbackExpression = null;
				shadowMatch = this.shadowMatchCache.get(targetMethod);
				if (shadowMatch == null) {
					Method methodToMatch = targetMethod;
					try {
						try {
							// 获取解析后的切点表达式，由于obtainPointcutExpression()方法在之前
							// 已经调用过一次，因而这里调用时可以直接从缓存中获取之前解析的结果。
							// 这里将解析后的切点表达式与当前方法进行匹配，并将匹配结果封装
							// 为一个ShadowMatch对象
							shadowMatch = obtainPointcutExpression().matchesMethodExecution(methodToMatch);
						}
						catch (ReflectionWorldException ex) {
							// Failed to introspect target method, probably because it has been loaded
							// in a special ClassLoader. Let's try the declaring ClassLoader instead...
							try {
								// 如果匹配失败，则在目标方法上找切点表达式，组装成为一个回调切点表达式，
								// 并且对回调切点表达式进行解析
								fallbackExpression = getFallbackPointcutExpression(methodToMatch.getDeclaringClass());
								if (fallbackExpression != null) {
									// 使用回调切点表达式与目标方法进行匹配
									shadowMatch = fallbackExpression.matchesMethodExecution(methodToMatch);
								}
							}
							catch (ReflectionWorldException ex2) {
								fallbackExpression = null;
							}
						}
						if (targetMethod != originalMethod && (shadowMatch == null ||
								(shadowMatch.neverMatches() && Proxy.isProxyClass(targetMethod.getDeclaringClass())))) {
							// Fall back to the plain original method in case of no resolvable match or a
							// negative match on a proxy class (which doesn't carry any annotations on its
							// redeclared methods).
							methodToMatch = originalMethod;
							try {
								// 如果目标方法与当前切点表达式匹配失败，则判断其原始方法与切点表达式匹配是否成功
								shadowMatch = obtainPointcutExpression().matchesMethodExecution(methodToMatch);
							}
							catch (ReflectionWorldException ex) {
								// Could neither introspect the target class nor the proxy class ->
								// let's try the original method's declaring class before we give up...
								try {
									// 获取原始方法上标注的切点表达式，作为回调切点表达式，并且对
									// 该切点表达式进行解析
									fallbackExpression = getFallbackPointcutExpression(methodToMatch.getDeclaringClass());
									if (fallbackExpression != null) {
										shadowMatch = fallbackExpression.matchesMethodExecution(methodToMatch);
									}
								}
								catch (ReflectionWorldException ex2) {
									fallbackExpression = null;
								}
							}
						}
					}
					catch (Throwable ex) {
						// Possibly AspectJ 1.8.10 encountering an invalid signature
						logger.debug("PointcutExpression matching rejected target method", ex);
						fallbackExpression = null;
					}
					// 这里如果目标方法和原始方法都无法与切点表达式匹配，就直接封装一个不匹配的结果到ShadowMatch中，并且返回
					if (shadowMatch == null) {
						shadowMatch = new ShadowMatchImpl(org.aspectj.util.FuzzyBoolean.NO, null, null, null);
					}
					else if (shadowMatch.maybeMatches() && fallbackExpression != null) {
						// 如果通过匹配结果无法立即判断当前方法是否与目标方法匹配，就将匹配得到的
						// ShadowMatch和回调的ShadowMatch封装到DefensiveShadowMatch中
						shadowMatch = new DefensiveShadowMatch(shadowMatch,
								fallbackExpression.matchesMethodExecution(methodToMatch));
					}
					// 将匹配结果缓存起来
					this.shadowMatchCache.put(targetMethod, shadowMatch);
				}
			}
		}
		return shadowMatch;
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AspectJExpressionPointcut)) {
			return false;
		}
		AspectJExpressionPointcut otherPc = (AspectJExpressionPointcut) other;
		return ObjectUtils.nullSafeEquals(this.getExpression(), otherPc.getExpression()) &&
				ObjectUtils.nullSafeEquals(this.pointcutDeclarationScope, otherPc.pointcutDeclarationScope) &&
				ObjectUtils.nullSafeEquals(this.pointcutParameterNames, otherPc.pointcutParameterNames) &&
				ObjectUtils.nullSafeEquals(this.pointcutParameterTypes, otherPc.pointcutParameterTypes);
	}

	@Override
	public int hashCode() {
		int hashCode = ObjectUtils.nullSafeHashCode(this.getExpression());
		hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.pointcutDeclarationScope);
		hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.pointcutParameterNames);
		hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode(this.pointcutParameterTypes);
		return hashCode;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("AspectJExpressionPointcut: ");
		sb.append("(");
		for (int i = 0; i < this.pointcutParameterTypes.length; i++) {
			sb.append(this.pointcutParameterTypes[i].getName());
			sb.append(" ");
			sb.append(this.pointcutParameterNames[i]);
			if ((i+1) < this.pointcutParameterTypes.length) {
				sb.append(", ");
			}
		}
		sb.append(")");
		sb.append(" ");
		if (getExpression() != null) {
			sb.append(getExpression());
		}
		else {
			sb.append("<pointcut expression not set>");
		}
		return sb.toString();
	}

	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization, just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		// pointcutExpression will be initialized lazily by checkReadyToMatch()
		this.shadowMatchCache = new ConcurrentHashMap<>(32);
	}


	/**
	 * Handler for the Spring-specific {@code bean()} pointcut designator
	 * extension to AspectJ.
	 * <p>This handler must be added to each pointcut object that needs to
	 * handle the {@code bean()} PCD. Matching context is obtained
	 * automatically by examining a thread local variable and therefore a matching
	 * context need not be set on the pointcut.
	 */
	private class BeanPointcutDesignatorHandler implements PointcutDesignatorHandler {

		private static final String BEAN_DESIGNATOR_NAME = "bean";

		@Override
		public String getDesignatorName() {
			return BEAN_DESIGNATOR_NAME;
		}

		@Override
		public ContextBasedMatcher parse(String expression) {
			return new BeanContextMatcher(expression);
		}
	}


	/**
	 * Matcher class for the BeanNamePointcutDesignatorHandler.
	 * <p>Dynamic match tests for this matcher always return true,
	 * since the matching decision is made at the proxy creation time.
	 * For static match tests, this matcher abstains to allow the overall
	 * pointcut to match even when negation is used with the bean() pointcut.
	 */
	private class BeanContextMatcher implements ContextBasedMatcher {

		private final NamePattern expressionPattern;

		public BeanContextMatcher(String expression) {
			this.expressionPattern = new NamePattern(expression);
		}

		@Override
		@SuppressWarnings("rawtypes")
		@Deprecated
		public boolean couldMatchJoinPointsInType(Class someClass) {
			return (contextMatch(someClass) == FuzzyBoolean.YES);
		}

		@Override
		@SuppressWarnings("rawtypes")
		@Deprecated
		public boolean couldMatchJoinPointsInType(Class someClass, MatchingContext context) {
			return (contextMatch(someClass) == FuzzyBoolean.YES);
		}

		@Override
		public boolean matchesDynamically(MatchingContext context) {
			return true;
		}

		@Override
		public FuzzyBoolean matchesStatically(MatchingContext context) {
			return contextMatch(null);
		}

		@Override
		public boolean mayNeedDynamicTest() {
			return false;
		}

		private FuzzyBoolean contextMatch(@Nullable Class<?> targetType) {
			String advisedBeanName = getCurrentProxiedBeanName();
			if (advisedBeanName == null) {  // no proxy creation in progress
				// abstain; can't return YES, since that will make pointcut with negation fail
				return FuzzyBoolean.MAYBE;
			}
			if (BeanFactoryUtils.isGeneratedBeanName(advisedBeanName)) {
				return FuzzyBoolean.NO;
			}
			if (targetType != null) {
				boolean isFactory = FactoryBean.class.isAssignableFrom(targetType);
				return FuzzyBoolean.fromBoolean(
						matchesBean(isFactory ? BeanFactory.FACTORY_BEAN_PREFIX + advisedBeanName : advisedBeanName));
			}
			else {
				return FuzzyBoolean.fromBoolean(matchesBean(advisedBeanName) ||
						matchesBean(BeanFactory.FACTORY_BEAN_PREFIX + advisedBeanName));
			}
		}

		private boolean matchesBean(String advisedBeanName) {
			return BeanFactoryAnnotationUtils.isQualifierMatch(
					this.expressionPattern::matches, advisedBeanName, beanFactory);
		}
	}


	private static class DefensiveShadowMatch implements ShadowMatch {

		private final ShadowMatch primary;

		private final ShadowMatch other;

		public DefensiveShadowMatch(ShadowMatch primary, ShadowMatch other) {
			this.primary = primary;
			this.other = other;
		}

		@Override
		public boolean alwaysMatches() {
			return this.primary.alwaysMatches();
		}

		@Override
		public boolean maybeMatches() {
			return this.primary.maybeMatches();
		}

		@Override
		public boolean neverMatches() {
			return this.primary.neverMatches();
		}

		@Override
		public JoinPointMatch matchesJoinPoint(Object thisObject, Object targetObject, Object[] args) {
			try {
				return this.primary.matchesJoinPoint(thisObject, targetObject, args);
			}
			catch (ReflectionWorldException ex) {
				return this.other.matchesJoinPoint(thisObject, targetObject, args);
			}
		}

		@Override
		public void setMatchingContext(MatchingContext aMatchContext) {
			this.primary.setMatchingContext(aMatchContext);
			this.other.setMatchingContext(aMatchContext);
		}
	}

}
