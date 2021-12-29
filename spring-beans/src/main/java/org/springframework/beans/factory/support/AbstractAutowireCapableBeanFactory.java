/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans.factory.support;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Abstract bean factory superclass that implements default bean creation,
 * with the full capabilities specified by the {@link RootBeanDefinition} class.
 * Implements the {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface in addition to AbstractBeanFactory's {@link #createBean} method.
 *
 * <p>Provides bean creation (with constructor resolution), property population,
 * wiring (including autowiring), and initialization. Handles runtime bean
 * references, resolves managed collections, calls initialization methods, etc.
 * Supports autowiring constructors, properties by name, and properties by type.
 *
 * <p>The main template method to be implemented by subclasses is
 * {@link #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)},
 * used for autowiring by type. In case of a factory which is capable of searching
 * its bean definitions, matching beans will typically be implemented through such
 * a search. For other factory styles, simplified matching algorithms can be implemented.
 *
 * <p>Note that this class does <i>not</i> assume or implement bean definition
 * registry capabilities. See {@link DefaultListableBeanFactory} for an implementation
 * of the {@link org.springframework.beans.factory.ListableBeanFactory} and
 * {@link BeanDefinitionRegistry} interfaces, which represent the API and SPI
 * view of such a factory, respectively.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Chris Beams
 * @author Sam Brannen
 * @since 13.02.2004
 * @see RootBeanDefinition
 * @see DefaultListableBeanFactory
 * @see BeanDefinitionRegistry
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	/** Strategy for creating bean instances. */
	private InstantiationStrategy instantiationStrategy = new CglibSubclassingInstantiationStrategy();

	/** Resolver strategy for method parameter names. */
	@Nullable
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	/** Whether to automatically try to resolve circular references between beans. */
	private boolean allowCircularReferences = true;

	/**
	 * Whether to resort to injecting a raw bean instance in case of circular reference,
	 * even if the injected bean eventually got wrapped.
	 */
	private boolean allowRawInjectionDespiteWrapping = false;

	/**
	 * Dependency types to ignore on dependency check and autowire, as Set of
	 * Class objects: for example, String. Default is none.
	 */
	private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

	/**
	 * Dependency interfaces to ignore on dependency check and autowire, as Set of
	 * Class objects. By default, only the BeanFactory interface is ignored.
	 */
	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	/**
	 * The name of the currently created bean, for implicit dependency registration
	 * on getBean etc invocations triggered from a user-specified Supplier callback.
	 */
	private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

	/** Cache of unfinished FactoryBean instances: FactoryBean name to BeanWrapper. */
	private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

	/** Cache of candidate factory methods per factory class. */
	private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();

	/** Cache of filtered PropertyDescriptors: bean Class to PropertyDescriptor array. */
	private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
			new ConcurrentHashMap<>();


	/**
	 * Create a new AbstractAutowireCapableBeanFactory.
	 */
	public AbstractAutowireCapableBeanFactory() {
		super();
		// 忽略给定接口的自动装配功能，比如实现了下面的接口
		// 举例：，当 A 中有属性 B，那么 Spring 在获取 A 的 Bean 的时候如果其属性 B 还没有
		// 初始化，那么 Spring 会自动初始化 B，这也是 Spring 中提供的一个重要特性。但是，某些情况
		// 下，B 不会被初始化，其中的一种情况就是 B 实现了 BeanNameAware 接口。Spring 中是这样
		// 介绍的：自动装配时忽略给定的依赖接口，典型应用是通过其他方式解析 Application 上下文注
		// 册依赖，类似于 BeanFactory 通过 BeanFactoryAware 进行注入或者 ApplicationContext 通过
		// ApplicationContextAware 进行注入
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
	}

	/**
	 * Create a new AbstractAutowireCapableBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 */
	public AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this();
		setParentBeanFactory(parentBeanFactory);
	}


	/**
	 * Set the instantiation strategy to use for creating bean instances.
	 * Default is CglibSubclassingInstantiationStrategy.
	 * @see CglibSubclassingInstantiationStrategy
	 */
	public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
		this.instantiationStrategy = instantiationStrategy;
	}

	/**
	 * Return the instantiation strategy to use for creating bean instances.
	 */
	protected InstantiationStrategy getInstantiationStrategy() {
		return this.instantiationStrategy;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed (e.g. for constructor names).
	 * <p>Default is a {@link DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * Return the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed.
	 */
	@Nullable
	protected ParameterNameDiscoverer getParameterNameDiscoverer() {
		//
		return this.parameterNameDiscoverer;
	}

	/**
	 * Set whether to allow circular references between beans - and automatically
	 * try to resolve them.
	 * <p>Note that circular reference resolution means that one of the involved beans
	 * will receive a reference to another bean that is not fully initialized yet.
	 * This can lead to subtle and not-so-subtle side effects on initialization;
	 * it does work fine for many scenarios, though.
	 * <p>Default is "true". Turn this off to throw an exception when encountering
	 * a circular reference, disallowing them completely.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans. Refactor your application logic to have the two beans
	 * involved delegate to a third bean that encapsulates their common logic.
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	/**
	 * Set whether to allow the raw injection of a bean instance into some other
	 * bean's property, despite the injected bean eventually getting wrapped
	 * (for example, through AOP auto-proxying).
	 * <p>This will only be used as a last resort in case of a circular reference
	 * that cannot be resolved otherwise: essentially, preferring a raw instance
	 * getting injected over a failure of the entire bean wiring process.
	 * <p>Default is "false", as of Spring 2.0. Turn this on to allow for non-wrapped
	 * raw beans injected into some of your references, which was Spring 1.2's
	 * (arguably unclean) default behavior.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans, in particular with auto-proxying involved.
	 * @see #setAllowCircularReferences
	 */
	public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
		this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
	}

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 */
	public void ignoreDependencyType(Class<?> type) {
		this.ignoredDependencyTypes.add(type);
	}

	/**
	 * Ignore the given dependency interface for autowiring.
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	public void ignoreDependencyInterface(Class<?> ifc) {
		this.ignoredDependencyInterfaces.add(ifc);
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof AbstractAutowireCapableBeanFactory) {
			AbstractAutowireCapableBeanFactory otherAutowireFactory =
					(AbstractAutowireCapableBeanFactory) otherFactory;
			this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
			this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
			this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
			this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
		}
	}


	//-------------------------------------------------------------------------
	// Typical methods for creating and populating external bean instances
	//-------------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public <T> T createBean(Class<T> beanClass) throws BeansException {
		// Use prototype bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass);
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(beanClass, getBeanClassLoader());
		return (T) createBean(beanClass.getName(), bd, null);
	}

	@Override
	public void autowireBean(Object existingBean) {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), getBeanClassLoader());
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public Object configureBean(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition mbd = getMergedBeanDefinition(beanName);
		RootBeanDefinition bd = null;
		if (mbd instanceof RootBeanDefinition) {
			RootBeanDefinition rbd = (RootBeanDefinition) mbd;
			bd = (rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition());
		}
		if (bd == null) {
			bd = new RootBeanDefinition(mbd);
		}
		if (!bd.isPrototype()) {
			bd.setScope(SCOPE_PROTOTYPE);
			bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), getBeanClassLoader());
		}
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(beanName, bd, bw);
		return initializeBean(beanName, existingBean, bd);
	}


	//-------------------------------------------------------------------------
	// Specialized methods for fine-grained control over the bean lifecycle
	//-------------------------------------------------------------------------

	@Override
	public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		return createBean(beanClass.getName(), bd, null);
	}

	@Override
	public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		if (bd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR) {
			return autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
		}
		else {
			Object bean;
			if (System.getSecurityManager() != null) {
				bean = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(bd, null, this),
						getAccessControlContext());
			}
			else {
				bean = getInstantiationStrategy().instantiate(bd, null, this);
			}
			populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
			return bean;
		}
	}

	@Override
	public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException {

		if (autowireMode == AUTOWIRE_CONSTRUCTOR) {
			throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
		}
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd =
				new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition bd = getMergedBeanDefinition(beanName);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
	}

	@Override
	public Object initializeBean(Object existingBean, String beanName) {
		return initializeBean(beanName, existingBean, null);
	}

	@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {
		Object result = existingBean;
		// 遍历所有注册的 BeanPostProcessor 实现类，调用 postProcessBeforeInitialization 方法
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			// 在 bean 初始化方法执行前，调用 postProcessBeforeInitialization 方法
			Object current = processor.postProcessBeforeInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {
		Object result = existingBean;
		// 在 bean 初始化后，遍历所有注册的 BeanPostProcessor 实现类，调用 postProcessAfterInitialization 方法
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			Object current = processor.postProcessAfterInitialization(result, beanName);
			if (current == null) {
				// 如果返回 null，则不会调用后续的 BeanPostProcessors
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public void destroyBean(Object existingBean) {
		new DisposableBeanAdapter(existingBean, getBeanPostProcessors(), getAccessControlContext()).destroy();
	}


	//-------------------------------------------------------------------------
	// Delegate methods for resolving injection points
	//-------------------------------------------------------------------------

	@Override
	public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
			return getBean(name, descriptor.getDependencyType());
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException {
		return resolveDependency(descriptor, requestingBeanName, null, null);
	}


	//---------------------------------------------------------------------
	// Implementation of relevant AbstractBeanFactory template methods
	//---------------------------------------------------------------------

	/**
	 * Central method of this class: creates a bean instance,
	 * populates the bean instance, applies post-processors, etc.
	 * @see #doCreateBean
	 */
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}
		RootBeanDefinition mbdToUse = mbd;

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition.
		// 在创建 beanDefinition 的时候，this.beanClass 存的是 加载类的'全限定类名'，所以第一次加载类的时候，this.beanClass 就
		// 是一个字符串，类加载完成后就会讲 class 对象存入 this.class 替换掉原来的字符串。
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			// 如果 resolvedClass 存在，并且 mdb 的 beanClass 类型不是 Class，并且 mdb 的 beanClass 不为空（则代表 beanClass 存的是 Class 的 name）,
			// 则使用 mdb 深拷贝一个新的 RootBeanDefinition 副本，并且将解析的 Class 赋值给拷贝的 RootBeanDefinition 副本的 beanClass 属性，
			// 该拷贝副本取代mdb用于后续的操作
			mbdToUse = new RootBeanDefinition(mbd);
			// 当类加载完成后 this.beanClass 就会存在是的该类的类对象
			mbdToUse.setBeanClass(resolvedClass);
		}

		// Prepare method overrides.
		try {
			// 在 Spring 的配置里面根本就没有诸如 override-method 之类的配置， 那么这个方法到底是干什么用的呢
			// 其实在 Spring 中确实没有 override-method 这样的配置，但是在 Spring 配置中是存
			// 在 lookup-method、replace-method 的，而这两个配置的加载其实就是将配置统一存放在 BeanDefinition 中
			// 的 methodOverrides 属性里，而这个函数的操作其实也就是针对于这两个配置的
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		try {
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			// resolveBeforeInstantiation() 调用 InstantiationAwareBeanPostProcessor 接口的 postProcessBeforeInstantiation 方法处理，
			// 扩展点，用于返回 bean 的代理对象，干涉 bean 的实例化。从代码中可以看到，resolveBeforeInstantiation 如果创建了 bean，则直接返回该实例。
			// 实例化前的处理，给 InstantiationAwareBeanPostProcessor 一个机会返回代理对象来替代真正的 bean 实例，达到“短路”效果
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
			// 当经过前置处理后返回的结果如果不为空，那么会直接略过后续的 bean 的创建而直接返
			// 回结果 这一特性虽然很容易被忽略，但是却起着至关重要的作用，我们熟知的 AOP 功能就
			// 是基于这里的判断的
			// 如果bean不为空，则会跳过Spring默认的实例化过程，直接使用返回的bean
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		try {
			// 创建原生 bean 实例。
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}
			// 返回创建的 Bean 实例
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			// A previously detected exception with proper bean creation context already,
			// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}

	/**
	 * Actually create the specified bean. Pre-creation processing has already happened
	 * at this point, e.g. checking {@code postProcessBeforeInstantiation} callbacks.
	 * <p>Differentiates between default bean instantiation, use of a
	 * factory method, and autowiring a constructor.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 * @see #instantiateBean
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 */
	protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		// Instantiate the bean.
		BeanWrapper instanceWrapper = null;
		// 如果是单例则需要首先清除缓存
		if (mbd.isSingleton()) {
			// 此时的 instanceWrapper 是从工厂缓存中移除的未完成的 FactoryBean 实例
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		// 如果工厂缓存中不存在则创建
		if (instanceWrapper == null) {
			// 从BeanWrapper 的 doc 中我们可以清楚的看到这个类的主要功能：https://blog.csdn.net/shuangyue/article/details/8585736
			// 实例化 bean ，将 BeanDefinition 转换为 BeanWrapper
			// 太复杂了，脑仁疼。参考链接：https://blog.csdn.net/v123411739/article/details/87994934 查看代码块4
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		// 拿到创建好的 Bean 实例
		Object bean = instanceWrapper.getWrappedInstance();
		// 拿到 Bean 实例的类型
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			mbd.resolvedTargetType = beanType;
		}

		// Allow post-processors to modify the merged bean definition.
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					// 应用后置处理器 MergedBeanDefinitionPostProcessor ，允许修改 MergedBeanDefinition，
					// @Autowired 注解正是通过此方法实现注入类型的预解析，并放入到缓存中。
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				mbd.postProcessed = true;
			}
		}

		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		// 判断是否需要提早曝光实例：单例 && 允许循环依赖 && 当前bean正在创建中，说白了就是出现了循环依赖
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			// 如果支持循环依赖则生成三级缓存，可以提前暴露bean
			// 三级缓存讲解：https://blog.csdn.net/u012098021/article/details/107352463
			// 在完成 Bean 的实例化后，属性注入之前 Spring 将 Bean 包装成一个工厂添加进了三级缓存中(singletonFactories)
			// () -> getEarlyBeanReference(beanName, mbd, bean)：主要用来判断是否需要 AOP 如果需要返回代理 Bean 对象，如果不需要就返回原始 Bean 对象
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
			// 等价于
			// 		  // 8.提前曝光beanName的ObjectFactory，用于解决循环引用
			//        addSingletonFactory(beanName, new ObjectFactory<Object>() {
			//            @Override
			//            public Object getObject() throws BeansException {
			//                // 8.1 应用后置处理器SmartInstantiationAwareBeanPostProcessor，允许返回指定bean的早期引用，若没有则直接返回bean
			//                return getEarlyBeanReference(beanName, mbd, bean);
			//            }
			//        });

		}

		// Initialize the bean instance.
		// 初始化 bean 实例
		Object exposedObject = bean;
		try {
			// 对 bean 进行属性填充；其中，可能存在依赖于其他 bean 的属性，则会递归初始化依赖的 bean 实例
			populateBean(beanName, mbd, instanceWrapper);
			// 初始化 bean，处理 Aware 接口并执行各类 bean 后处理器，执行初始化方法(比如 init-method )，如果需要生成aop代理对象
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			}
			else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}


		// 这个地方就很复杂
		// 如果这个bean同时存在循环依赖和AOP的情况
		// 假设一个场景：
		// 1. A 依赖 B，B 也依赖 A
		// 2. A 中存在 AOP 的切点，也就是 A 会被 AOP 代理
		// 解读：
		// 先加载 A ,因为 A 存在循环依赖，所以在 A 在实例化后，会将当 A 包装进 lambda 表达式中，并放入 singletonFactories 中，
		// 当进行依赖注入的时候，会发现依赖 B，就会去实例化 B
		// B 实例化后，进行属性填充的时候，发现依赖 A，这时 B 就会去 singletonFactories 中去获取 A 的实例，这时返回的 A 就是一
		// 个经过 Spring 初始化后置处理器处理得到的一个 AOP 的代理对象，并将 A 从 singletonFactories 移除，放入 earlySingleton 中。
		// B 创建完成后，将 B 填充到 A 的属性中，然后 A 继续执行初始化，最后也会执行 Spring 初始化后置处理器处理，但是因为在前面
		// 已经经过 AOP 代理了，所以这里就不会在走后 AOP 代理的逻辑。
		// 这是就存在一个问题，B 中的 A 属性填充的是 A 的代理对象，而最开始创建的 A 还是一个没有经过 AOP 代理的原始的 Bean，这时
		// 就有问题了，下文就是对这个问题的处理
		// 如果"属性填充"，"实例化"之前的bean和之后的bean一样，那么就去 earlySingleton 中将之前创建的 A 的 AOP 代理对象取出来，替
		// 换当前的 bean。
		if (earlySingletonExposure) {
			// 如果允许提前曝光实例，则进行循环依赖检查
			// 若存在循环依赖则代理对象提前生成，这里需要把后续原始bean置换成代理bean，否则返回的最终bean不是代理bean
			Object earlySingletonReference = getSingleton(beanName, false);
			// 由于上一行传入的参数是false，所以earlySingletonReference只有在当前解析的bean存在循环依赖的情况下才会不为空
			if (earlySingletonReference != null) {
				// 如果 exposedObject 没有在初始化方法中被改变，也就是没有被增强
				// 如果 exposedObject 没有在initializeBean方法中被增强，则不影响之前的循环引用
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				// 如果exposedObject在initializeBean方法中被增强 && 不允许在循环引用的情况下使用注入原始bean实例
				// && 当前 bean 又被其他bean依赖
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					// 拿到依赖当前bean的所有bean的beanName数组
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						// 尝试移除这些bean的实例，因为这些bean依赖的bean已经被增强了，他们依赖的bean相当于脏数据
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							// 移除失败的添加到 actualDependentBea
							actualDependentBeans.add(dependentBean);
						}
					}
					// 因为 bean 创建后其所依赖的 bean 一定是已经创建的，
					// actualDependentBeans 不为空则表示当前 bean 创建后其依赖的 bean 却没有被
					// 全部创建完，也就是说存在循环依赖
					if (!actualDependentBeans.isEmpty()) {
						// 场景一：通过构造器注入构成的循环依赖， 此依赖是无法解决的 ，只能抛出 BeanCurrentlyInCreationException 异常表示循环依赖
						// 如果存在移除失败的，则抛出异常，因为存在bean依赖了“脏数据”
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		// Register bean as disposable.
		try {
			// 触发条件，spring容器关闭的时候。例如context.close()，或者 annotationConfigApplicationContext.registerShutdownHook();

			// 如果配置了 destroy-method ，这里需要注册以便于在销毁时候调用
			// 注册用于销毁的bean，执行销毁操作的有三种：自定义destroy方法、DisposableBean接口、DestructionAwareBeanPostProcessor， 对
			// 应 org.springframework.beans.factory.support.DisposableBeanAdapter.destroy 方法

			// 参考连接：https://blog.csdn.net/weixin_44367006/article/details/104504179
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		// 完成创建并返回
		return exposedObject;
	}

	@Override
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		// 拿到beanName的类型
		Class<?> targetType = determineTargetType(beanName, mbd, typesToMatch);
		// Apply SmartInstantiationAwareBeanPostProcessors to predict the
		// eventual type after a before-instantiation shortcut.
		// 应用SmartInstantiationAwareBeanPostProcessors后置处理器，来预测实例化的最终类型，
		// SmartInstantiationAwareBeanPostProcessors继承了InstantiationAwareBeanPostProcessor，
		// InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation方法可以改变Bean实例的类型，
		// 而SmartInstantiationAwareBeanPostProcessors的predictBeanType方法可以预测这个类型
		if (targetType != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					// 调用predictBeanType方法
					Class<?> predicted = ibp.predictBeanType(targetType, beanName);
					// 如果predicted不为空 && (typesToMatch长度不为1 || typesToMatch[0]不为FactoryBean.class ||
					// predicted是FactoryBean本身、子类或子接口)，则返回predicted
					if (predicted != null && (typesToMatch.length != 1 || FactoryBean.class != typesToMatch[0] ||
							FactoryBean.class.isAssignableFrom(predicted))) {
						return predicted;
					}
				}
			}
		}
		// 否则返回beanName的类型
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 */
	@Nullable
	protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType == null) {
			targetType = (mbd.getFactoryMethodName() != null ?
					getTypeForFactoryMethod(beanName, mbd, typesToMatch) :
					resolveBeanClass(mbd, beanName, typesToMatch));
			if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
				mbd.resolvedTargetType = targetType;
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition which is based on
	 * a factory method. Only called if there is no singleton instance registered
	 * for the target bean already.
	 * <p>This implementation determines the type matching {@link #createBean}'s
	 * different creation strategies. As far as possible, we'll perform static
	 * type checking to avoid creation of the target bean.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see #createBean
	 */
	@Nullable
	protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
		if (cachedReturnType != null) {
			return cachedReturnType.resolve();
		}

		Class<?> factoryClass;
		boolean isStatic = true;

		String factoryBeanName = mbd.getFactoryBeanName();
		if (factoryBeanName != null) {
			if (factoryBeanName.equals(beanName)) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"factory-bean reference points back to the same bean definition");
			}
			// Check declared factory method return type on factory class.
			factoryClass = getType(factoryBeanName);
			isStatic = false;
		}
		else {
			// Check declared factory method return type on bean class.
			factoryClass = resolveBeanClass(mbd, beanName, typesToMatch);
		}

		if (factoryClass == null) {
			return null;
		}
		factoryClass = ClassUtils.getUserClass(factoryClass);

		// If all factory methods have the same return type, return that type.
		// Can't clearly figure out exact method due to type converting / autowiring!
		Class<?> commonType = null;
		Method uniqueCandidate = null;
		int minNrOfArgs =
				(mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0);
		Method[] candidates = this.factoryMethodCandidateCache.computeIfAbsent(
				factoryClass, ReflectionUtils::getUniqueDeclaredMethods);

		for (Method candidate : candidates) {
			if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate) &&
					candidate.getParameterCount() >= minNrOfArgs) {
				// Declared type variables to inspect?
				if (candidate.getTypeParameters().length > 0) {
					try {
						// Fully resolve parameter names and argument values.
						Class<?>[] paramTypes = candidate.getParameterTypes();
						String[] paramNames = null;
						ParameterNameDiscoverer pnd = getParameterNameDiscoverer();
						if (pnd != null) {
							paramNames = pnd.getParameterNames(candidate);
						}
						ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
						Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
						Object[] args = new Object[paramTypes.length];
						for (int i = 0; i < args.length; i++) {
							ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
									i, paramTypes[i], (paramNames != null ? paramNames[i] : null), usedValueHolders);
							if (valueHolder == null) {
								valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
							}
							if (valueHolder != null) {
								args[i] = valueHolder.getValue();
								usedValueHolders.add(valueHolder);
							}
						}
						Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(
								candidate, args, getBeanClassLoader());
						uniqueCandidate = (commonType == null && returnType == candidate.getReturnType() ?
								candidate : null);
						commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
						if (commonType == null) {
							// Ambiguous return types found: return null to indicate "not determinable".
							return null;
						}
					}
					catch (Throwable ex) {
						if (logger.isDebugEnabled()) {
							logger.debug("Failed to resolve generic return type for factory method: " + ex);
						}
					}
				}
				else {
					uniqueCandidate = (commonType == null ? candidate : null);
					commonType = ClassUtils.determineCommonAncestor(candidate.getReturnType(), commonType);
					if (commonType == null) {
						// Ambiguous return types found: return null to indicate "not determinable".
						return null;
					}
				}
			}
		}

		mbd.factoryMethodToIntrospect = uniqueCandidate;
		if (commonType == null) {
			return null;
		}
		// Common return type found: all factory methods return same type. For a non-parameterized
		// unique candidate, cache the full type declaration context of the target factory method.
		cachedReturnType = (uniqueCandidate != null ?
				ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType));
		mbd.factoryMethodReturnType = cachedReturnType;
		return cachedReturnType.resolve();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, a full creation of the FactoryBean is
	 * used as fallback (through delegation to the superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	@Nullable
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		if (mbd.getInstanceSupplier() != null) {
			ResolvableType targetType = mbd.targetType;
			if (targetType != null) {
				Class<?> result = targetType.as(FactoryBean.class).getGeneric().resolve();
				if (result != null) {
					return result;
				}
			}
			if (mbd.hasBeanClass()) {
				Class<?> result = GenericTypeResolver.resolveTypeArgument(mbd.getBeanClass(), FactoryBean.class);
				if (result != null) {
					return result;
				}
			}
		}

		String factoryBeanName = mbd.getFactoryBeanName();
		String factoryMethodName = mbd.getFactoryMethodName();

		if (factoryBeanName != null) {
			if (factoryMethodName != null) {
				// Try to obtain the FactoryBean's object type from its factory method declaration
				// without instantiating the containing bean at all.
				BeanDefinition fbDef = getBeanDefinition(factoryBeanName);
				if (fbDef instanceof AbstractBeanDefinition) {
					AbstractBeanDefinition afbDef = (AbstractBeanDefinition) fbDef;
					if (afbDef.hasBeanClass()) {
						Class<?> result = getTypeForFactoryBeanFromMethod(afbDef.getBeanClass(), factoryMethodName);
						if (result != null) {
							return result;
						}
					}
				}
			}
			// If not resolvable above and the referenced factory bean doesn't exist yet,
			// exit here - we don't want to force the creation of another bean just to
			// obtain a FactoryBean's object type...
			if (!isBeanEligibleForMetadataCaching(factoryBeanName)) {
				return null;
			}
		}

		// Let's obtain a shortcut instance for an early getObjectType() call...
		FactoryBean<?> fb = (mbd.isSingleton() ?
				getSingletonFactoryBeanForTypeCheck(beanName, mbd) :
				getNonSingletonFactoryBeanForTypeCheck(beanName, mbd));

		if (fb != null) {
			// Try to obtain the FactoryBean's object type from this early stage of the instance.
			Class<?> result = getTypeForFactoryBean(fb);
			if (result != null) {
				return result;
			}
			else {
				// No type found for shortcut FactoryBean instance:
				// fall back to full creation of the FactoryBean instance.
				return super.getTypeForFactoryBean(beanName, mbd);
			}
		}

		if (factoryBeanName == null && mbd.hasBeanClass()) {
			// No early bean instantiation possible: determine FactoryBean's type from
			// static factory method signature or from class inheritance hierarchy...
			if (factoryMethodName != null) {
				return getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
			}
			else {
				return GenericTypeResolver.resolveTypeArgument(mbd.getBeanClass(), FactoryBean.class);
			}
		}

		return null;
	}

	/**
	 * Introspect the factory method signatures on the given bean class,
	 * trying to find a common {@code FactoryBean} object type declared there.
	 * @param beanClass the bean class to find the factory method on
	 * @param factoryMethodName the name of the factory method
	 * @return the common {@code FactoryBean} object type, or {@code null} if none
	 */
	@Nullable
	private Class<?> getTypeForFactoryBeanFromMethod(Class<?> beanClass, String factoryMethodName) {

		/**
		 * Holder used to keep a reference to a {@code Class} value.
		 */
		class Holder {

			@Nullable
			Class<?> value = null;
		}

		final Holder objectType = new Holder();

		// CGLIB subclass methods hide generic parameters; look at the original user class.
		Class<?> fbClass = ClassUtils.getUserClass(beanClass);

		// Find the given factory method, taking into account that in the case of
		// @Bean methods, there may be parameters present.
		ReflectionUtils.doWithMethods(fbClass, method -> {
			if (method.getName().equals(factoryMethodName) &&
					FactoryBean.class.isAssignableFrom(method.getReturnType())) {
				Class<?> currentType = GenericTypeResolver.resolveReturnTypeArgument(method, FactoryBean.class);
				if (currentType != null) {
					objectType.value = ClassUtils.determineCommonAncestor(currentType, objectType.value);
				}
			}
		});

		return (objectType.value != null && Object.class != objectType.value ? objectType.value : null);
	}

	/**
	 * Obtain a reference for early access to the specified bean,
	 * typically for the purpose of resolving a circular reference.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param bean the raw bean instance
	 * @return the object to expose as bean reference
	 */
	protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
		// 对 bean 再一次依赖引用, 主要应用 SmartInstantiationAwareBeanPostProcessor
		// 其中我们熟知的 AOP 就是在这里将 advice 动态织入 beaη 中，若没有则直接返 bean，不做任何处理
		Object exposedObject = bean;
		// 如果bean不为空 && mbd不是合成 && 存在InstantiationAwareBeanPostProcessors
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			// 应用所有SmartInstantiationAwareBeanPostProcessor，调用getEarlyBeanReference方法
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					// 允许SmartInstantiationAwareBeanPostProcessor返回指定bean的早期引用
					// 只有出现了循环依赖，才会走到这里，盗用 getEarlyBeanReference 方法
					exposedObject = ibp.getEarlyBeanReference(exposedObject, beanName);
				}
			}
		}
		// 返回要作为bean引用公开的对象，如果没有SmartInstantiationAwareBeanPostProcessor修改，则返回的是入参的bean对象本身
		return exposedObject;
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Obtain a "shortcut" singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		synchronized (getSingletonMutex()) {
			BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
			if (bw != null) {
				return (FactoryBean<?>) bw.getWrappedInstance();
			}
			Object beanInstance = getSingleton(beanName, false);
			if (beanInstance instanceof FactoryBean) {
				return (FactoryBean<?>) beanInstance;
			}
			if (isSingletonCurrentlyInCreation(beanName) ||
					(mbd.getFactoryBeanName() != null && isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
				return null;
			}

			Object instance;
			try {
				// Mark this bean as currently in creation, even if just partially.
				beforeSingletonCreation(beanName);
				// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
				instance = resolveBeforeInstantiation(beanName, mbd);
				if (instance == null) {
					bw = createBeanInstance(beanName, mbd, null);
					instance = bw.getWrappedInstance();
				}
			}
			catch (UnsatisfiedDependencyException ex) {
				// Don't swallow, probably misconfiguration...
				throw ex;
			}
			catch (BeanCreationException ex) {
				// Instantiation failure, maybe too early...
				if (logger.isDebugEnabled()) {
					logger.debug("Bean creation exception on singleton FactoryBean type check: " + ex);
				}
				onSuppressedException(ex);
				return null;
			}
			finally {
				// Finished partial creation of this bean.
				afterSingletonCreation(beanName);
			}

			FactoryBean<?> fb = getFactoryBean(beanName, instance);
			if (bw != null) {
				this.factoryBeanInstanceCache.put(beanName, bw);
			}
			return fb;
		}
	}

	/**
	 * Obtain a "shortcut" non-singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		if (isPrototypeCurrentlyInCreation(beanName)) {
			return null;
		}

		Object instance;
		try {
			// Mark this bean as currently in creation, even if just partially.
			beforePrototypeCreation(beanName);
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			instance = resolveBeforeInstantiation(beanName, mbd);
			if (instance == null) {
				BeanWrapper bw = createBeanInstance(beanName, mbd, null);
				instance = bw.getWrappedInstance();
			}
		}
		catch (UnsatisfiedDependencyException ex) {
			// Don't swallow, probably misconfiguration...
			throw ex;
		}
		catch (BeanCreationException ex) {
			// Instantiation failure, maybe too early...
			if (logger.isDebugEnabled()) {
				logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
			}
			onSuppressedException(ex);
			return null;
		}
		finally {
			// Finished partial creation of this bean.
			afterPrototypeCreation(beanName);
		}

		return getFactoryBean(beanName, instance);
	}

	/**
	 * Apply MergedBeanDefinitionPostProcessors to the specified bean definition,
	 * invoking their {@code postProcessMergedBeanDefinition} methods.
	 * @param mbd the merged bean definition for the bean
	 * @param beanType the actual type of the managed bean instance
	 * @param beanName the name of the bean
	 * @see MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition
	 */
	protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
		// 获取BeanFactory中已注册的BeanPostProcessor
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof MergedBeanDefinitionPostProcessor) {
				// 调用MergedBeanDefinitionPostProcessor的postProcessMergedBeanDefinition方法，
				MergedBeanDefinitionPostProcessor bdp = (MergedBeanDefinitionPostProcessor) bp;
				bdp.postProcessMergedBeanDefinition(mbd, beanType, beanName);
			}
		}
	}

	/**
	 * Apply before-instantiation post-processors, resolving whether there is a
	 * before-instantiation shortcut for the specified bean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the shortcut-determined bean instance, or {@code null} if none
	 */
	@Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		// 如果尚未被解析
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			// Make sure bean class is actually resolved at this point.
			// mbd 不是合成的，并且 BeanFactory 中存在 InstantiationAwareBeanPostProcessor
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
				// 解析 beanName 对应的 Bean 实例的类型
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
					// 实例化前的后置处理器
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
						// 如果返回的bean不为空，会跳过Spring默认的实例化过程，
						// 所以只能在这里调用 BeanPostProcessor 实现类的 postProcessAfterInitialization 方法
						// 主要功能就是，如果当前 bean 需要被代理的话，只能在这里调用初始化后置处理器
						// 参考链接：https://blog.csdn.net/v123411739/article/details/87954818 中的代码块4
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			// 如果 bean 不为空，则将 beforeInstantiationResolved 赋值为true，代表在实例化之前已经解析
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}

	/**
	 * Apply InstantiationAwareBeanPostProcessors to the specified bean definition
	 * (by class and name), invoking their {@code postProcessBeforeInstantiation} methods.
	 * <p>Any returned object will be used as the bean instead of actually instantiating
	 * the target bean. A {@code null} return value from the post-processor will
	 * result in the target bean being instantiated.
	 * @param beanClass the class of the bean to be instantiated
	 * @param beanName the name of the bean
	 * @return the bean object to use instead of a default instance of the target bean, or {@code null}
	 * @see InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
	 */
	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
		// 遍历当前BeanFactory中的BeanPostProcessor
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			// 应用InstantiationAwareBeanPostProcessor后置处理器，允许postProcessBeforeInstantiation方法返回bean对象的代理
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
				// 执行postProcessBeforeInstantiation方法，在Bean实例化前操作，
				// 该方法可以返回一个构造完成的Bean实例，从而不会继续执行创建Bean实例的“正规的流程”，达到“短路”的效果
				Object result = ibp.postProcessBeforeInstantiation(beanClass, beanName);
				if (result != null) {
					// 如果result不为空，也就是有后置处理器返回了bean实例对象，则会跳过Spring默认的实例化过程
					return result;
				}
			}
		}
		return null;
	}

	/**
	 * Create a new instance for the specified bean, using an appropriate instantiation strategy:
	 * factory method, constructor autowiring, or simple instantiation.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a BeanWrapper for the new instance
	 * @see #obtainFromSupplier
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 * @see #instantiateBean
	 */
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// Make sure bean class is actually resolved at this point.
		// 解析bean的类型信息
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		// beanClass不为空 && beanClass不是公开类（不是public修饰） && 该bean不允许访问非公共构造函数和方法，则抛异常
		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}

		// String 提供的一种机制
		// AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		//		context.register(AopConfig.class);
		//
		//		AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition().getBeanDefinition();
		//		beanDefinition.setBeanClass(UserService.class);
		//		beanDefinition.setInstanceSupplier(new Supplier<Object>() {
		//			@Override
		//			public Object get() {
		//				return new UserService();
		//			}
		//		});
		//		context.registerBeanDefinition("userService", beanDefinition);
		//
		//		context.refresh();
		//
		//		UserService userService = (UserService) context.getBean("userService");
		//		userService.test();
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			return obtainFromSupplier(instanceSupplier, beanName);
		}

		// 如果工厂方法不为空则使用工厂方法初始化策略
		if (mbd.getFactoryMethodName() != null) {
			// 如果在 RootBeanDefinition 中存在 factoryMethodName 属性，或者说在配置文件中配置了
			// factory-method ，那么 Spring 会尝试使 instantiateUsingFactoryMethod(beanName, mbd, args) 方法
			// 根据 RootBeanDefinition 中的配置生 bean 的实例
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		// Shortcut when re-creating the same bean...
		// resolved: 构造函数或工厂方法是否已经解析过
		boolean resolved = false;
		// autowireNecessary: 是否需要自动注入（即是否需要解析构造函数参数）
		boolean autowireNecessary = false;
		if (args == null) {
			// 加锁
			synchronized (mbd.constructorArgumentLock) {
				// 一个类有多个构造函数，每个构造函数都有不同的参数，所以调用前需要先根据参数锁定构造函数或对应的工厂方法

				// 解析构造函数并进行构造函数的实例化。因为一个 bean 对应的类中可能会有多个构造
				// 函数，而每个构造函数的参数不同， spring 在根据参数及类型去判断最终会使用哪个构造函数
				// 进行实例化。但是，判断的过程是个比较消耗性能的步骤，所以采用缓存机制，如果已经解析过则
				// 不需要重复解析而是直接从 RootBeanDefinition 中的属性 resolvedConstructorOrFactoryMethod 缓存
				// 的值去取，否则需要再次解析，将解析的结果添加至 RootBeanDefinition 中的属性 resolvedConstructorOrFactoryMethod 中
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					// 如果resolvedConstructorOrFactoryMethod缓存不为空，则将resolved标记为已解析
					resolved = true;
					// 根据constructorArgumentsResolved判断是否需要自动注入,也就是如果找到的构造方法有参数就为true，如果是无参构造方法就
					// 是false
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		// 如果已经解析过则使用解析好的构造函数方法不需要再次锁定
		// 如果已经解析过，则使用resolvedConstructorOrFactoryMethod缓存里解析好的构造函数方法
		if (resolved) {
			if (autowireNecessary) {
				// 需要自动注入，则执行构造函数自动注入
				// 拿到缓存好的有参构造方法的入参属性的值
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				// 否则使用默认的构造函数进行bean的实例化，默认的构造方法就是无参构造方法
				return instantiateBean(beanName, mbd);
			}
		}

		// Candidate constructors for autowiring?
		// 需要根据参数解析构造函数
		// 一个类有多个构造函数，每个构造函数都有不同的参数，所以调用前需要先根据参数锁定构造函数或对应的工厂方法
		// 应用后置处理器SmartInstantiationAwareBeanPostProcessor，拿到bean的候选构造函数
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
		// 如果ctors不为空 || mbd的注入方式为AUTOWIRE_CONSTRUCTOR || mdb定义了构造函数的参数值 || args不为空，则执行构造函数自动注入
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			// 构造函数自动注入（通常指带有参数的构造函数）
			// ctors 这里的的内容包括，@Autowire(require=false)修饰的，和无参的构造方法
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// Preferred constructors for default construction?
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
			return autowireConstructor(beanName, mbd, ctors, null);
		}

		// No special handling: simply use no-arg constructor.
		// 没有特殊处理，则使用默认的构造函数进行bean的实例化
		return instantiateBean(beanName, mbd);
	}

	/**
	 * Obtain a bean instance from the given supplier.
	 * @param instanceSupplier the configured supplier
	 * @param beanName the corresponding bean name
	 * @return a BeanWrapper for the new instance
	 * @since 5.0
	 * @see #getObjectForBeanInstance
	 */
	protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		Object instance;

		String outerBean = this.currentlyCreatedBean.get();
		this.currentlyCreatedBean.set(beanName);
		try {
			instance = instanceSupplier.get();
		}
		finally {
			if (outerBean != null) {
				this.currentlyCreatedBean.set(outerBean);
			}
			else {
				this.currentlyCreatedBean.remove();
			}
		}

		if (instance == null) {
			instance = new NullBean();
		}
		BeanWrapper bw = new BeanWrapperImpl(instance);
		initBeanWrapper(bw);
		return bw;
	}

	/**
	 * Overridden in order to implicitly register the currently created bean as
	 * dependent on further beans getting programmatically retrieved during a
	 * {@link Supplier} callback.
	 * @since 5.0
	 * @see #obtainFromSupplier
	 */
	@Override
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		String currentlyCreatedBean = this.currentlyCreatedBean.get();
		if (currentlyCreatedBean != null) {
			registerDependentBean(beanName, currentlyCreatedBean);
		}

		return super.getObjectForBeanInstance(beanInstance, name, beanName, mbd);
	}

	/**
	 * Determine candidate constructors to use for the given bean, checking all registered
	 * {@link SmartInstantiationAwareBeanPostProcessor SmartInstantiationAwareBeanPostProcessors}.
	 * @param beanClass the raw class of the bean
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors
	 */
	@Nullable
	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName)
			throws BeansException {

		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			// 遍历所有的BeanPostProcessor
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					// 调用SmartInstantiationAwareBeanPostProcessor的determineCandidateConstructors方法，
					// 该方法可以返回要用于beanClass的候选构造函数
					// 例如：使用@Autowire注解修饰构造函数，则该构造函数在这边会被AutowiredAnnotationBeanPostProcessor找到
					Constructor<?>[] ctors = ibp.determineCandidateConstructors(beanClass, beanName);
					if (ctors != null) {
						// 如果ctors不为空，则不再继续执行其他的SmartInstantiationAwareBeanPostProcessor
						return ctors;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Instantiate the given bean using its default constructor.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			if (System.getSecurityManager() != null) {
				beanInstance = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(mbd, beanName, this),
						getAccessControlContext());
			}
			else {
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
			}
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);
			initBeanWrapper(bw);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * mbd parameter specifies a class, rather than a factoryBean, or an instance variable
	 * on a factory object itself configured using Dependency Injection.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 * @see #getBean(String, Object[])
	 */
	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
	}

	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param ctors the chosen candidate constructors
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {

		// see again
		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}

	/**
	 * Populate the bean instance in the given BeanWrapper with the property values
	 * from the bean definition.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param bw the BeanWrapper with bean instance
	 */
	@SuppressWarnings("deprecation")  // for postProcessPropertyValues
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		if (bw == null) {
			// 如果 bw 为空，属性不为空，抛异常，无法将属性值应用于 null 实例
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				// 如果 bw 为空，属性也为空，则跳过
				return;
			}
		}

		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
		// 给 InstantiationAwareBeanPostProcessors 最后一次机会在属性设置前来改变 bean
		// 如果 mbd 不是合成的 && 存在 InstantiationAwareBeanPostProcessor，则遍历处理I nstantiationAwareBeanPostProcessor
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					// ibp.postProcessAfterInstantiation 该接口通过返回 false 能中断属性的注入过程。
					// 在 bean 实例化后，属性填充之前被调用，允许修改 bean 的属性
					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
						return;
					}
				}
			}
		}

		// 返回此 bean 的属性值
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);
		// 解析自动装配模式为 AUTOWIRE_BY_NAME 和 AUTOWIRE_BY_TYPE（现在几乎不用）
		// 例如使用 @Bean(autowire=Autowire.BY_TYPE)
		// 注意：这里和 @Autowire 注解没有半毛钱关系
		// 为什么有 @Bean(autowire=Autowire.BY_TYPE) 这个注解还会出现 @Autowire 注解呢，就是因为前者太灵活了
		// 因为 AUTOWIRE_BY_NAME 会找到 bean 中，所用以 set 开头的方法，比如 setXxx，就会将 xxx 解析成一个 beanName
		// 并调用 setXxx 方法进行属性填充
		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			// Add property values based on autowire by name if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}
			// Add property values based on autowire by type if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}
			pvs = newPvs;
		}

		// BeanFactory 是否注册过 InstantiationAwareBeanPostProcessors
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		// 是否需要依赖检查
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

		PropertyDescriptor[] filteredPds = null;
		if (hasInstAwareBpps) {
			// 如果我们在 MergedBeanDefinitionPostProcessor 等处理器中，已经设置了当前 bean 中某些属性的值
			// 并放入到了 PropertyValues 中，也就是 pvs ！= null ，即使属性上也加了 @Autowire 注解，Spring 也会使用我们自己设置
			// 的 value 值，而不会使用 @Autowire 自动装配的找到的属性值进行属性填充。虽然我们自己设置了属性值，但是 @Autowire 的处理
			// 逻辑也会执行，也给给 bean 实例对象的属性值赋值，但是在最后的 applyPropertyValues 方法中，会使用 pvs 中我们设置的属性值
			// 将 @Autowire 设置的属性值给替换掉。
			// 在 @Resource 中会通过 checkPropertySkipping(pvs) 方法来进行校验
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			// 应用 InstantiationAwareBeanPostProcessor 处理器的 postProcessPropertyValues 方法，对属性
			// 获取完毕填充前对属性的再次处理，典型应用是 RequiredAnnotationBeanPostProcessor 类中对属性的验证
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				// 应用后置处理器InstantiationAwareBeanPostProcessor
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					// 应用后置处理器 InstantiationAwareBeanPostProcessor 的 postProcessProperties 方法，
					// 进行属性填充前的再次处理。例子：现在最常用的 @Autowire 属性注入就是在这注入依赖的 bean 实例对象的

					// 处理 @Resource：CommonAnnotationBeanPostProcessor.java
					// 处理 @Autowire、@Value：AutowiredAnnotationBeanPostProcessor.java
					PropertyValues pvsToUse = ibp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
					if (pvsToUse == null) {
						if (filteredPds == null) {
							filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
						}
						// 对所有需要依赖检查的属性进行后处理
						pvsToUse = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
						if (pvsToUse == null) {
							return;
						}
					}
					pvs = pvsToUse;
				}
			}
		}
		if (needsDepCheck) {
			if (filteredPds == null) {
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			// 依赖检查，对应 depends-on 属性，3.0已经弃用此属性
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

		if (pvs != null) {
			// 将所有 PropertyValue 属性填充至 BeanWrapper 中
			// AUTOWIRE_BY_NAME 和 AUTOWIRE_BY_TYPE 解析出来的 值都会放到 PropertyValue 中
			// @Autowire 解析出来的依赖对象，在他自己的处理逻辑中，就已经完成了属性的赋值，并没有放到 PropertyValue 中
			// 最后 bean 实例中的属性值，都会以 PropertyValues 中的值为准，并通过 applyPropertyValues 进行赋值
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}

	/**
	 * Fill in any missing property values with references to
	 * other beans in this factory if autowire is set to "byName".
	 * @param beanName the name of the bean we're wiring up.
	 * Useful for debugging messages; not used functionally.
	 * @param mbd bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {
		// 在传人的参数 pvs 中找出已经加载的 bean 并递归实例化，进而加入到 pvs 中

		// 寻找bw中需要依赖注入的属性
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			// 校验是否存在beanName=propertyName的bean实例或者BeanDefinition
			if (containsBean(propertyName)) {
				// 获取propertyName的bean实例对象
				Object bean = getBean(propertyName);
				// 将属性名和属性值添加到pvs
				pvs.add(propertyName, bean);
				// 注册依赖关系到缓存（beanName依赖propertyName）
				registerDependentBean(propertyName, beanName);
				if (logger.isTraceEnabled()) {
					logger.trace("Added autowiring by name from bean name '" + beanName +
							"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
							"' by name: no matching bean found");
				}
			}
		}
	}

	/**
	 * Abstract method defining "autowire by type" (bean properties by type) behavior.
	 * <p>This is like PicoContainer default, in which there must be exactly one bean
	 * of the property type in the bean factory. This makes bean factories simple to
	 * configure for small namespaces, but doesn't work as well as standard Spring
	 * behavior for bigger applications.
	 * @param beanName the name of the bean to autowire by type
	 * @param mbd the merged bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByType(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}

		// 实现根据名称自动匹配的第一步就是寻找 bw 中需要依赖注入的属性，同样对于根据类型
		// 自动匹配的实现来讲第一步也是 bw 中需要依赖注入的属性，然后遍历这些属性并寻找类
		// 型匹配的 bean， 其中最复杂的就是寻找类型匹配的 bean 同时， Spring 中提供了对集合的类型
		// 注入的支持，如使用注解的方式：
		// @Autowired
		// private List<Test> tests;
		// Spring 把所有与 Test 匹配的类型找出来并注入到 tests 属性中，正是由于这一因素，
		// 所以在 autowireByType 函数中，新建了局部变量 autowiredBeanNames ，用于存储所有依赖的
		// bean ，如果只是对非集合类的属性注入来说，此属性并无用处。
		// 对于寻找类型匹配的逻辑实现封装在了 resolveDependency 函数中
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
		// 寻找bw中需要依赖注入的属性
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			try {
				// 寻找 bw 中需要注入的依赖属性
				PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
				// Don't try autowiring by type for type Object: never makes sense,
				// even if it technically is a unsatisfied, non-simple property.
				if (Object.class != pd.getPropertyType()) {
					// 获取指定属性的set方法，封装成MethodParameter（必须有set方法才能通过属性来注入）
					MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
					// Do not allow eager init for type matching in case of a prioritized post-processor.
					boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
					// 将MethodParameter的方法参数索引信息封装成DependencyDescriptor
					DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
					// 解析指定 beanName 属性所匹配的值，并将解析到的属性名称存储在
					// autowiredBeanName中，当属性存在多个封装的bean时，如：
					// @Autowired private List<A> aList; 将会找到所有匹配 A 类型的 bean 并将其注入
					Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
					if (autowiredArgument != null) {
						// 如果找到了依赖的bean实例，将属性名和bean实例放到pvs中
						pvs.add(propertyName, autowiredArgument);
					}
					for (String autowiredBeanName : autowiredBeanNames) {
						// 注册依赖关系，beanName依赖autowiredBeanName
						registerDependentBean(autowiredBeanName, beanName);
						if (logger.isTraceEnabled()) {
							logger.trace("Autowiring by type from bean name '" + beanName + "' via property '" +
									propertyName + "' to bean named '" + autowiredBeanName + "'");
						}
					}
					autowiredBeanNames.clear();
				}
			}
			catch (BeansException ex) {
				throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
			}
		}
	}


	/**
	 * Return an array of non-simple bean properties that are unsatisfied.
	 * These are probably unsatisfied references to other beans in the
	 * factory. Does not include simple properties like primitives or Strings.
	 * @param mbd the merged bean definition the bean was created with
	 * @param bw the BeanWrapper the bean was created with
	 * @return an array of bean property names
	 * @see org.springframework.beans.BeanUtils#isSimpleProperty
	 */
	protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
		Set<String> result = new TreeSet<>();
		// 拿到mdb的属性值
		PropertyValues pvs = mbd.getPropertyValues();
		// 拿到bw的PropertyDescriptors
		PropertyDescriptor[] pds = bw.getPropertyDescriptors();
		// 遍历bw的PropertyDescriptors
		for (PropertyDescriptor pd : pds) {
			// pd用于写入属性值的方法不为空 && pd不是从依赖性检查中排除的bean属性 && pd不包含在pvs里
			// && pd的属性类型不是“简单”属性（基础类型、枚举、Number等）
			if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
					!BeanUtils.isSimpleProperty(pd.getPropertyType())) {
				// 符合条件，则添加pd的name到result中
				result.add(pd.getName());
			}
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @param cache whether to cache filtered PropertyDescriptors for the given bean Class
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 * @see #filterPropertyDescriptorsForDependencyCheck(org.springframework.beans.BeanWrapper)
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
		PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
		if (filtered == null) {
			filtered = filterPropertyDescriptorsForDependencyCheck(bw);
			if (cache) {
				PropertyDescriptor[] existing =
						this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
				if (existing != null) {
					filtered = existing;
				}
			}
		}
		return filtered;
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
		List<PropertyDescriptor> pds = new ArrayList<>(Arrays.asList(bw.getPropertyDescriptors()));
		pds.removeIf(this::isExcludedFromDependencyCheck);
		return pds.toArray(new PropertyDescriptor[0]);
	}

	/**
	 * Determine whether the given bean property is excluded from dependency checks.
	 * <p>This implementation excludes properties defined by CGLIB and
	 * properties whose type matches an ignored dependency type or which
	 * are defined by an ignored dependency interface.
	 * @param pd the PropertyDescriptor of the bean property
	 * @return whether the bean property is excluded
	 * @see #ignoreDependencyType(Class)
	 * @see #ignoreDependencyInterface(Class)
	 */
	protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
		return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
				this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
				AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
	}

	/**
	 * Perform a dependency check that all properties exposed have been set,
	 * if desired. Dependency checks can be objects (collaborating beans),
	 * simple (primitives and String), or all (both).
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition the bean was created with
	 * @param pds the relevant property descriptors for the target bean
	 * @param pvs the property values to be applied to the bean
	 * @see #isExcludedFromDependencyCheck(java.beans.PropertyDescriptor)
	 */
	protected void checkDependencies(
			String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, @Nullable PropertyValues pvs)
			throws UnsatisfiedDependencyException {

		int dependencyCheck = mbd.getDependencyCheck();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && (pvs == null || !pvs.contains(pd.getName()))) {
				boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
				boolean unsatisfied = (dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_ALL) ||
						(isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
						(!isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
				if (unsatisfied) {
					throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, pd.getName(),
							"Set this property value or disable dependency checking for this bean.");
				}
			}
		}
	}

	/**
	 * Apply the given property values, resolving any runtime references
	 * to other beans in this bean factory. Must use deep copy, so we
	 * don't permanently modify this property.
	 * @param beanName the bean name passed for better exception information
	 * @param mbd the merged bean definition
	 * @param bw the BeanWrapper wrapping the target object
	 * @param pvs the new property values
	 */
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		if (pvs.isEmpty()) {
			return;
		}

		if (System.getSecurityManager() != null && bw instanceof BeanWrapperImpl) {
			((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
		}

		MutablePropertyValues mpvs = null;
		List<PropertyValue> original;

		// 获取属性值列表
		if (pvs instanceof MutablePropertyValues) {
			mpvs = (MutablePropertyValues) pvs;
			// 如果 mpvs 中的值已经被转换为对应的类型那么可以直接设置到 beanWrapper 中
			if (mpvs.isConverted()) {
				// Shortcut: use the pre-converted values as-is.
				try {
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values", ex);
				}
			}
			original = mpvs.getPropertyValueList();
		}
		else {
			// 如果 pvs 并不是使用 MutablePropertyValues 封装的类型，那么直接使用原始的属性获取方法
			original = Arrays.asList(pvs.getPropertyValues());
		}

		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}
		// 获取对应的解析器
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

		// Create a deep copy, resolving any references for values.
		// 创建深层拷贝副本，用于存放解析后的属性值
		List<PropertyValue> deepCopy = new ArrayList<>(original.size());
		boolean resolveNecessary = false;
		// 遍历属性，将属性转换为对应类的对应属性的类型
		for (PropertyValue pv : original) {
			if (pv.isConverted()) {
				// 如果pv已经包含转换的值，则直接添加到deepCopy
				deepCopy.add(pv);
			}
			else {
				// 否则，进行转换
				// 拿到pv的原始属性名和属性值
				String propertyName = pv.getName();
				Object originalValue = pv.getValue();
				// 使用解析器解析原始属性值，在里面创建的依赖bean
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
				Object convertedValue = resolvedValue;
				// 判断该属性是否可转换
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				// 如果可转换，则转换指定目标属性的给定值
				if (convertible) {
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				// Possibly store converted value in merged bean definition,
				// in order to avoid re-conversion for every created bean instance.
				// 在合并的BeanDefinition中存储转换后的值，以避免为每个创建的bean实例重新转换
				if (resolvedValue == originalValue) {
					if (convertible) {
						pv.setConvertedValue(convertedValue);
					}
					deepCopy.add(pv);
				}
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				}
				else {
					resolveNecessary = true;
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
		if (mpvs != null && !resolveNecessary) {
			mpvs.setConverted();
		}

		// Set our (possibly massaged) deep copy.
		try {
			// 设置bean的属性值为deepCopy
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Error setting property values", ex);
		}
	}

	/**
	 * Convert the given value for the specified target property.
	 */
	@Nullable
	private Object convertForProperty(
			@Nullable Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

		if (converter instanceof BeanWrapperImpl) {
			return ((BeanWrapperImpl) converter).convertForProperty(value, propertyName);
		}
		else {
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
			return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
		}
	}


	/**
	 * Initialize the given bean instance, applying factory callbacks
	 * as well as init methods and bean post processors.
	 * <p>Called from {@link #createBean} for traditionally defined beans,
	 * and from {@link #initializeBean} for existing bean instances.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @return the initialized bean instance (potentially wrapped)
	 * @see BeanNameAware
	 * @see BeanClassLoaderAware
	 * @see BeanFactoryAware
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #invokeInitMethods
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
		// 激活Aware方法
		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		}
		else {
			// 对特殊的 bean 处理 Aware、BeanClassLoaderAware、BeanFactoryAware
			invokeAwareMethods(beanName, bean);
			// 还有一些其他的 Aware 的使用，比如 BeanFactoryAware、ApplicationContextAware、ResourceLoaderAware、
			// ServletContextAware 等，这些是在 postProcessorsBeforeInitialization 方法中处理的
		}

		Object wrappedBean = bean;
		if (mbd == null || !mbd.isSynthetic()) {
			// 在初始化前应用 BeanPostProcessor的postProcessBeforeInitialization 方法，允许对 bean 实例进行包装
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

		try {
			// 调用 initMethod 方法，这里主要两类 init，一类是实现了 InitializingBean 接口，如果实现了，则调用它的afterPropertiesSet()方法；
			// 另外，若在 bean 的定义中指定了 init-method 方法，则调用用户自定义的 init-method。注：两类 init 方法都存在时，
			// 会先调用 InitializingBean 的方法。然后再调用 init-method 指定的方法。
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}
		if (mbd == null || !mbd.isSynthetic()) {
			// 在初始化后应用 BeanPostProcessor的postProcessAfterInitialization 方法，允许对 bean实例进行包装
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}
		// 返回wrappedBean
		return wrappedBean;
	}

	/**
	 * @param beanName
	 * @param bean
	 */
	private void invokeAwareMethods(String beanName, Object bean) {
		if (bean instanceof Aware) {
			//  BeanNameAware: 实现此接口的类想要拿到 beanName，因此我们在这边赋值给它
			if (bean instanceof BeanNameAware) {
				// 因为 BeanNameAware 是个接口，所以实现类必然会重写 setBeanName() 方法，这
				// 里就是调用实现类的 setBeanName() 方法进行赋值
				((BeanNameAware) bean).setBeanName(beanName);
			}
			// BeanClassLoaderAware：实现此接口的类想要拿到 beanClassLoader，因此我们在这边赋值给它
			if (bean instanceof BeanClassLoaderAware) {
				ClassLoader bcl = getBeanClassLoader();
				if (bcl != null) {
					((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
				}
			}
			// BeanFactoryAware: 实现此接口的类想要拿到 BeanFactory，因此我们在这边赋值给它
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}

	/**
	 * Give a bean a chance to react now all its properties are set,
	 * and a chance to know about its owning bean factory (this object).
	 * This means checking whether the bean implements InitializingBean or defines
	 * a custom init method, and invoking the necessary callback(s) if it does.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the merged bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @throws Throwable if thrown by init methods or by the invocation process
	 * @see #invokeCustomInitMethod
	 */
	protected void  invokeInitMethods(String beanName, Object bean, @Nullable RootBeanDefinition mbd)
			throws Throwable {

		// 首先检查 bean 是否实现了 InitializingBean 接口，如果是的话调用 afterPropertiesSet 方法
		boolean isInitializingBean = (bean instanceof InitializingBean);
		if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			}
			if (System.getSecurityManager() != null) {
				try {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						((InitializingBean) bean).afterPropertiesSet();
						return null;
					}, getAccessControlContext());
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				// 属性初始化后的处理
				((InitializingBean) bean).afterPropertiesSet();
			}
		}

		if (mbd != null && bean.getClass() != NullBean.class) {
			String initMethodName = mbd.getInitMethodName();
			if (StringUtils.hasLength(initMethodName) &&
					!(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.isExternallyManagedInitMethod(initMethodName)) {
				// 调用自定义初始化方法
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}

	/**
	 * Invoke the specified custom init method on the given bean.
	 * Called by invokeInitMethods.
	 * <p>Can be overridden in subclasses for custom resolution of init
	 * methods with arguments.
	 * @see #invokeInitMethods
	 */
	protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd)
			throws Throwable {

		// 拿到初始化方法的方法名
		String initMethodName = mbd.getInitMethodName();
		Assert.state(initMethodName != null, "No init method set");
		// 根据方法名拿到方法
		Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));

		if (initMethod == null) {
			// 如果不存在initMethodName对应的方法，并且是强制执行初始化方法(默认为强制), 则抛出异常
			if (mbd.isEnforceInitMethod()) {
				throw new BeanDefinitionValidationException("Could not find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("No default init method named '" + initMethodName +
							"' found on bean with name '" + beanName + "'");
				}
				// Ignore non-existent default lifecycle methods.
				// 如果设置了非强制，找不到则直接返回
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
		}
		Method methodToInvoke = ClassUtils.getInterfaceMethodIfPossible(initMethod);

		// 调用初始化方法
		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(methodToInvoke);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>)
						() -> methodToInvoke.invoke(bean), getAccessControlContext());
			}
			catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		}
		else {
			try {
				ReflectionUtils.makeAccessible(methodToInvoke);
				methodToInvoke.invoke(bean);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * Applies the {@code postProcessAfterInitialization} callback of all
	 * registered BeanPostProcessors, giving them a chance to post-process the
	 * object obtained from FactoryBeans (for example, to auto-proxy them).
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	@Override
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
		return applyBeanPostProcessorsAfterInitialization(object, beanName);
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanInstanceCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanInstanceCache.clear();
		}
	}

	/**
	 * Expose the logger to collaborating delegates.
	 * @since 5.0.7
	 */
	Log getLogger() {
		return logger;
	}


	/**
	 * Special DependencyDescriptor variant for Spring's good old autowire="byType" mode.
	 * Always optional; never considering the parameter name for choosing a primary candidate.
	 */
	@SuppressWarnings("serial")
	private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {

		public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
			super(methodParameter, false, eager);
		}

		@Override
		public String getDependencyName() {
			return null;
		}
	}

}
