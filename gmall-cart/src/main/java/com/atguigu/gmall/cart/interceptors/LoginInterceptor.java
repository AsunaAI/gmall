package com.atguigu.gmall.cart.interceptors;

import com.atguigu.core.utils.CookieUtils;
import com.atguigu.core.utils.JwtUtils;
import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.core.bean.UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private JwtProperties jwtProperties;

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserInfo userInfo = new UserInfo();
        String token = CookieUtils.getCookieValue(request, this.jwtProperties.getCookieName());
        String userKey = CookieUtils.getCookieValue(request, this.jwtProperties.getUserKey());
        if (StringUtils.isBlank(userKey)) {
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request, response, this.jwtProperties.getUserKey(), userKey, 6 * 30 * 24 * 3600);
        }
        userInfo.setUserKey(userKey);
        if (StringUtils.isNotBlank(token)) {
            Map<String, Object> infoFromToken = JwtUtils.getInfoFromToken(token, this.jwtProperties.getPublicKey());
            userInfo.setId((Long) (infoFromToken.get("id")));
        }
        THREAD_LOCAL.set(userInfo);
        return true;
    }

    public static UserInfo getUserInfo() {
        return THREAD_LOCAL.get();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        THREAD_LOCAL.remove();
    }
}
