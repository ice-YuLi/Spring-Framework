package com.zsj.core.test.controller;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Test {
	public static void main(String[] args) {

//		String name = "朱嘉豪";
//		replaceStr(name);
//		name = Optional.ofNullable(name).ifPresent(item -> {
//			item = "我是猪";
//		});
//		System.out.println(name);
//	MyTestBean myTestBean = new MyTestBean();
//	myTestBean.setName("一朵小花");
//
//	MyTestBean myTestBean2 = myTestBean;
//	myTestBean2.setName("一棵小草");
//
//	System.out.println(myTestBean.getName());
//	System.out.println(myTestBean2.getName());

//		set -> set.add(beanName), set -> !this.beanDefinitionMap.containsKey(beanName
//		updateManualSingletonNames(set -> set.remove("只升杰"), set -> set.contains("只升杰"));
//		updateManualSingletonNames(new Consumer(){
//
//			@Override
//			public void accept(Object o) {
//
//			}

//			@Override
//			public Consumer andThen(Consumer after) {
//				return null;
//			}
//
//		}, set -> set.contains("只升杰"));
//		updateManualSingletonNames(new Consumer<Set<String>>(){
//
//			@Override
//			public void accept(Set<String> set) {
//
//			}
//
//			@Override
//			public Consumer<Set<String>> andThen(Consumer<? super Set<String>> after) {
//				return null;
//			}
//		}, set -> set.contains("只升杰"));
//
//		new Consumer<Set<String>>(){
//
//			@Override
//			public void accept(Set<String> set) {
//
//			}
//
//			@Override
//			public Consumer<Set<String>> andThen(Consumer<? super Set<String>> after) {
//				return null;
//			}
//		};
	}

	public static void updateManualSingletonNames(Consumer<Set<String>> action, Predicate<Set<String>> condition){
		System.out.println(action);
		System.out.println(condition);
	}

	public static String replaceStr (String name){
		return name = "只升杰";
	}
}
