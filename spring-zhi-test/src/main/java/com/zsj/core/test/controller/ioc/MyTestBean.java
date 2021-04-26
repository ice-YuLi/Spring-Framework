package com.zsj.core.test.controller.ioc;

public class MyTestBean {

	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void outStr() {
		System.out.println("我是猪！！！");
	}
}
