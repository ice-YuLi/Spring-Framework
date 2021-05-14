package com.zsj.core.test.controller.aop;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {

	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("file:/Users/zhishengjie/workspace/IdeaProjects/Spring-Framework/spring-zhi-test/src/main/java/com/zsj/core/test/controller/aop/dynamic/spring.xml");
		TestBean bean = (TestBean) ac.getBean("test");
		bean.test();
		// 切入点 AopNamespaceHandler
	}
}
