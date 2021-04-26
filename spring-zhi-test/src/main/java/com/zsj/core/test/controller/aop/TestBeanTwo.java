package com.zsj.core.test.controller.aop;

public class TestBeanTwo {

	private String testStr = "testStr";

	public String getTestStr() {
		return testStr;
	}

	public void setTestStr(String testStr) {
		this.testStr = testStr;
	}

	public void test() {
		System.out.println("test");
	}
}
