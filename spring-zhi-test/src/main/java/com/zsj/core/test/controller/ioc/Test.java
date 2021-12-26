package com.zsj.core.test.controller.ioc;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Test {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("file:G:/development/workspace/ideaworkspace/Spring-Framework/spring-zhi-test/src/main/java/com/zsj/core/test/controller/ioc/spring-two.xml");
		ac.getEnvironment().setActiveProfiles("dev");
//		BeanFactory ac = new XmlBeanFactory(new ClassPathResource("classpath:applicationContext.xml"));
//		Resource resource = new ClassPathResource("classpath:applicationContext.xml");
//		Resource resource = new ClassPathResource("applicationContext.xml");
//		BeanFactory ac = new XmlBeanFactory(resource);

//		MyTestBeanFactoryBean bean = (MyTestBeanFactoryBean) ac.getBean("&myTestBean");
		MyTestBean bean = (MyTestBean) ac.getBean("myTestBean");
		System.out.println(bean);
		
		bean.outStr();
	}
}
