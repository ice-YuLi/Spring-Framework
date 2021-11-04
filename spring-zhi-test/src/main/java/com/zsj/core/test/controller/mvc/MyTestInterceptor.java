package com.zsj.core.test.controller.mvc;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MyTestInterceptor implements HandlerInterceptor {

	// 方法前执行
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

		long startTime = System.currentTimeMillis();
		request.setAttribute("startTime", startTime);
		// 该返回 true ，允许 DispatcherServlet 继续处理请求。否则，DispatcherServlet 会认为这
		// 方法已经处理了请求，直接将响应返回给用户。
		return true;
	}

	// 方法后执行
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

		long startTime = (Long) request.getAttribute("startTime");
		request.removeAttribute("startTime");
		long endTime = System.currentTimeMillis();
		modelAndView.addObject("handlingTime", endTime - startTime);
	}

	// 在所有请求处理完成之后被调用。（如视图呈现之后）
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

		HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
	}
}
