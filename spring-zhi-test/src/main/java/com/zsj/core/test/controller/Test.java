package com.zsj.core.test.controller;

import com.zsj.core.test.controller.proxy.UserInterface;
import com.zsj.core.test.controller.proxy.UserService;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
//import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.context.annotation.Bean;

import javax.servlet.DispatcherType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;

public class Test {
	public static void main(String[] args) {
		System.out.printf(String.valueOf(DispatcherType.REQUEST));

		UserService target = new UserService();

		UserInterface userInterface = (UserInterface)Proxy.newProxyInstance(UserService.class.getClassLoader(), UserService.class.getInterfaces(), new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				System.out.println("=========jdk===========");
				return method.invoke(target, args);
			}
		});

		userInterface.test();

//		Enhancer enhancer = new Enhancer();
//		enhancer.setSuperclass(UserService.class);
//		enhancer.setCallbacks(new Callback[]{new MethodInterceptor() {
//			@Override
//			public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
//				System.out.println("=========cglib===========");
//				return method.invoke(target, objects);
//			}
//		}});
//
//		UserService userService = (UserService) enhancer.create();
//		userService.test();

		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTarget(new UserService());
		proxyFactory.addAdvice(new org.aopalliance.intercept.MethodInterceptor() {
			@Override
			public Object invoke(MethodInvocation invocation) throws Throwable {
				return null;
			}
		});
	}

	@Bean
	public ProxyFactoryBean userServiceProxy(){
		UserService userService = new UserService();

		ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
		proxyFactoryBean.setTarget(userService);
		proxyFactoryBean.addAdvice(new MethodInterceptor() {
			@Override
			public Object invoke(MethodInvocation invocation) throws Throwable {
				System.out.println("before...");
				Object result = invocation.proceed();
				System.out.println("after...");
				return result;
			}
		});
		return proxyFactoryBean;
	}

	@Bean
	public BeanNameAutoProxyCreator beanNameAutoProxyCreator() {
		BeanNameAutoProxyCreator beanNameAutoProxyCreator = new BeanNameAutoProxyCreator();
		beanNameAutoProxyCreator.setBeanNames("userSe*");
		beanNameAutoProxyCreator.setInterceptorNames("zhouyuAroundAdvise");
		beanNameAutoProxyCreator.setProxyTargetClass(true);

		return beanNameAutoProxyCreator;
	}


}
