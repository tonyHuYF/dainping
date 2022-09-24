package com.tony.dainping.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.tony.dainping.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从头部获取token
        String token = request.getHeader("authorization");

        //判断token是否存在
        if (StrUtil.isBlank(token)) {
            //不存在，拦截，设置返回状态码401
            response.setStatus(401);
            return false;
        }

        String key = RedisConstants.LOGIN_USER_KEY + token;

        //从redis中拿到user用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        //判断是否存在
        if (userMap.isEmpty()) {
            //不存在，拦截，设置返回状态码401
            response.setStatus(401);
            return false;
        }

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //将用户保存到ThreadLocal中
        UserHolder.saveUser(userDTO);

        //刷新过期时间
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //销毁用户信息，防止内存泄露
        UserHolder.removeUser();
    }
}
