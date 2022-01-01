package com.zsj.core.test.controller.proxy;

import com.zsj.core.test.controller.proxy.advice.MyAdvice;
import org.aopalliance.aop.Advice;
import org.springframework.aop.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

import java.lang.reflect.Method;

public class MySpringProxyFactory {

	public static void main(String[] arg){

		UserInterface userService = new UserService();

		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTarget(userService);
		proxyFactory.setTargetClass(userService.getClass());
//		proxyFactory.setTargetSource();
//		proxyFactory.setInterfaces(UserInterface.class);
		proxyFactory.addAdvice(new MethodBeforeAdvice() {
			@Override
			public void before(Method method, Object[] args, Object target) throws Throwable {
				System.out.println("-----proxy-----");
			}
		});

//		proxyFactory.addAdvice(new MyAdvice());

		proxyFactory.addAdvisor(new PointcutAdvisor() {
			@Override
			public Pointcut getPointcut() {
				return new StaticMethodMatcherPointcut() {
					@Override
					public boolean matches(Method method, Class<?> targetClass) {
						return method.getName().equals("test");
					}
				};
			}

			@Override
			public Advice getAdvice() {
				return new MyAdvice();
			}

			@Override
			public boolean isPerInstance() {
				return false;
			}
		});

		proxyFactory.addAdvisor(new PointcutAdvisor() {
			@Override
			public Pointcut getPointcut() {
				return new Pointcut() {
					@Override
					public ClassFilter getClassFilter() {
						return new ClassFilter() {
							@Override
							public boolean matches(Class<?> clazz) {
								return clazz.equals(UserService.class);
							}
						};
					}

					@Override
					public MethodMatcher getMethodMatcher() {
						return new MethodMatcher() {
							@Override
							public boolean matches(Method method, Class<?> targetClass) {
								return method.getName().equals("test");
							}

							@Override
							public boolean isRuntime() {
								return false;
							}

							@Override
							public boolean matches(Method method, Class<?> targetClass, Object... args) {
								return false;
							}
						};
					}
				};
			}

			@Override
			public Advice getAdvice() {
				return null;
			}

			@Override
			public boolean isPerInstance() {
				return false;
			}
		});

		UserInterface proxy = (UserInterface) proxyFactory.getProxy();
		proxy.test();
		System.out.println("===========================================");
		proxy.ouPut();

	}
}
