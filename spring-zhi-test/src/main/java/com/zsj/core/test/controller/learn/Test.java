package com.zsj.core.test.controller.learn;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Test {


	public static void main(String[] args) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AopConfig.class);
		UserService userService = (UserService) context.getBean("userService");
		userService.test();
	}
}
