package com.tony.dainping.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断ThreadLocal里是否有用户信息
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }

        //放行
        return true;
    }

}
