package com.zsj.core.test.controller.ioc;

import org.springframework.beans.factory.DisposableBean;

public class MyTestBean implements DisposableBean{

	private String name;

	public String getName() {
		System.out.println("调用getName()方法");
		return name;
	}

	public void setName(String name) {
		System.out.println("调用setName()方法");
		this.name = name;
	}

	public void outStr() {
		System.out.println(name);
	}

	@Override
	public void destroy() throws Exception {
		System.out.println("MyTestBean 要销毁了");
	}
}
