package com.zsj.core.test.controller.proxy.advice;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.ThrowsAdvice;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class MyAdvice implements
		MethodBeforeAdvice,
//		MethodInterceptor,
//		AfterReturningAdvice,
		ThrowsAdvice {

	public Object invoke(MethodInvocation invocation) throws Throwable {
		System.out.println("-----invoke-----");
		invocation.proceed();
		return null;
	}

	public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
		System.out.println("-----afterReturning-----");
	}

	public void before(Method method, Object[] args, Object target) throws Throwable {
		System.out.println("-----before-----");
	}

	public void afterThrowing(Method method, Object[] args, Object target, NullPointerException exception) throws Throwable {
		System.out.println("-----afterThrowing-----");
	}
}
