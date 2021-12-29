package com.zsj.core.test.controller.learn;

import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import java.util.LinkedHashSet;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.function.Supplier;

public class Test {

	public static void main(String[] args) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(AopConfig.class);
		context.refresh();
		UserService userService = (UserService) context.getBean("userService");
		userService.test();

//		Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
//		autowiredBeanNames.add("1");
//		autowiredBeanNames.add("2");
//		autowiredBeanNames.add("3");
//		System.out.println(autowiredBeanNames);

	}
}
