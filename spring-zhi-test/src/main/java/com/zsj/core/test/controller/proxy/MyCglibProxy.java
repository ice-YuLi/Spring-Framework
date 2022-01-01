package com.zsj.core.test.controller.proxy;

import org.springframework.cglib.proxy.*;

import java.lang.reflect.Method;

public class MyCglibProxy {

	public static void main(String[] arg) {

		UserService target = new UserService();

		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(UserService.class);
		enhancer.setCallbacks(new Callback[]{
				new MethodInterceptor() {
					@Override
					public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {

						// o 代理创建好的代理对象
						// method 增强的方法名
						// objects 方法的入参
						// methodProxy 代理方法

						System.out.println("-----proxy-----");
						method.invoke(target, objects);
//						methodProxy.invokeSuper(o, objects);

						return null;
					}
				}, NoOp.INSTANCE});

		enhancer.setCallbackFilter(new CallbackFilter() {
			@Override
			public int accept(Method method) {
				if(method.getName().equals("test")){
					return 0;
				}
				return 1;
			}
		});

		UserService userService = (UserService) enhancer.create();
		userService.test();
		System.out.println("===========================================");
		userService.ouPut();
	}
}
