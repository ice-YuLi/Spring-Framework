package com.zsj.core.test.controller.aop;

import org.springframework.beans.factory.annotation.Autowired;

public class TestBean {

	private TestBeanTwo testTwo;

	public TestBeanTwo getTestTwo() {
		return testTwo;
	}

	public void setTestTwo(TestBeanTwo testTwo) {
		this.testTwo = testTwo;
	}

	private String testStr = "testStr";

	public String getTestStr() {
		return testStr;
	}

	public void setTestStr(String testStr) {
		this.testStr = testStr;
	}

	public void test() {
		testTwo.test();
	}
}
