package com.zsj.core.test.controller.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

@Aspect
public class AspectJTestTwo {

	@Pointcut("execution(* *.test(..))")
	public void test() {

	}

	@Before("test()")
	public void beforeTest() {
		System.out.println("beforeTest2");
	}

	@After("test()")
	public void afterTest() {
		System.out.println("afterTest2");
	}

	@Around("test()")
	public Object arountTest(ProceedingJoinPoint p) {
		System.out.println("before12");
		Object o = null;
		try {
			o = p.proceed();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		System.out.println("after12");
		return o;
	}
}