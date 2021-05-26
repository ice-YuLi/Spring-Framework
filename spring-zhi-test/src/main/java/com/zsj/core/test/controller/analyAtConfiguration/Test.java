package com.zsj.core.test.controller.analyAtConfiguration;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 参考链接：https://www.cnblogs.com/think-in-java/p/11876997.html
public class Test {
	public static void main(String args[]) {
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext(AppConfig.class);
	}
}

@Configuration
//@EnableAspectJAutoProxy
class AppConfig {

	@Bean
	public A a() {
		b();
		return new A();
	}

	@Bean
	public B b() {
		return new B();
	}
}

class A {
	public A() {
		System.out.println("Call A constructor");
	}
}

class B {
	public B() {
		System.out.println("Call B constructor");
	}
}

