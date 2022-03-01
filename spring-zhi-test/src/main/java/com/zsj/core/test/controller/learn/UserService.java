package com.zsj.core.test.controller.learn;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

@Component
//@ComponentScan("com.zsj.core.test.controller.ioc")
public class UserService {

	private OrderService orderService;

	private OrderService orderService2;

	public UserService() {
	}

	public UserService(OrderService orderService) {
		this.orderService = orderService;
	}

	public UserService(OrderService orderService, OrderService orderService2) {
		this.orderService = orderService;
		this.orderService2 = orderService2;
	}

	//	private OrderService orderService;
//
//	public void test(){
//		System.out.println("输出XXXX: " + orderService);
//	}
//
//	public OrderService getOrderService() {
//		return orderService;
//	}
//
//	public void setOrderService(OrderService orderService) {
//		this.orderService = orderService;
//	}
}
