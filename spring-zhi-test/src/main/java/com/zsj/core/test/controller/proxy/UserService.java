package com.zsj.core.test.controller.proxy;

import org.springframework.stereotype.Component;

@Component
public class UserService implements UserInterface{

	public void test(){
		System.out.println("-----test-----");
//		throw new NullPointerException();
	}

	public void ouPut(){
		System.out.println("-----ouPut-----");
	}
}
