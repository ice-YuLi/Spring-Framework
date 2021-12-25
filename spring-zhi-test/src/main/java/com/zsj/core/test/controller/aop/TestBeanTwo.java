package com.zsj.core.test.controller.aop;

import org.springframework.beans.factory.annotation.Autowired;

public class TestBeanTwo {

	@Autowired
	private TestBean testBean;

	private String testStr = "testStr";

	public String getTestStr() {
		return testStr;
	}

	public void setTestStr(String testStr) {
		this.testStr = testStr;
	}

	public void test() {
		System.out.println("testTwo");
	}
}
