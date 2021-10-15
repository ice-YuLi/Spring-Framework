package com.zsj.core.test.controller.aop;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {

	private static final Log LOGGER = LogFactory.getLog(Test.class);

	public static void main(String[] args) {
		LOGGER.debug("步骤一");
		ApplicationContext ac = new ClassPathXmlApplicationContext("file:/Users/zhishengjie/workspace/IdeaProjects/Spring-Framework/spring-zhi-test/src/main/java/com/zsj/core/test/controller/aop/dynamic/spring.xml");
		TestBean bean = (TestBean) ac.getBean("test");
		bean.test();
		// 切入点 AopNamespaceHandler
	}
}
