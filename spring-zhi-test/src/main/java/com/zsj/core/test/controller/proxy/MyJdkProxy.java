package com.zsj.core.test.controller.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class MyJdkProxy {

	public static void main(String[] arg){

		UserInterface target = new UserService();

		UserInterface proxy = (UserInterface) Proxy.newProxyInstance(MyJdkProxy.class.getClassLoader(), new Class<?>[]{UserInterface.class}, new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

				System.out.println("-----proxy-----");

				method.invoke(target, args);

				return null;
			}
		});

		proxy.test();
	}
}
