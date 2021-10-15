package com.zsj.core.test.controller.ioc;

public class MyTestBean {

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
}
