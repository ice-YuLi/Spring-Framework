package com.zsj.core.test.controller.proxy;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

public class MyAspect {

	@Pointcut(value = "(execution(public * com.csii..submit(..))) && (@annotation(com.csii.web.core.OprNameLog)) && args(a, b)", argNames = "a, b")
	public void aop(String a, String b) {}

	@Before("aop()")
	public void before(String a, String b){

	}

	@After(value = "(execution(public * com.csii..submit(..))) && (@annotation(com.csii.web.core.OprNameLog)) && args(a, b)", argNames = "a, b")
	public void after(String a, String b){

	}
}
