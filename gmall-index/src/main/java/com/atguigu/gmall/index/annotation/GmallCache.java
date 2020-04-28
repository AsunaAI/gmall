package com.atguigu.gmall.index.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {

	//缓存中的key前缀
	String prefix() default "";

	//缓存过期时间，单位分钟
	int timeout() default 5;

	//缓存过期时间的随机值范围，单位分钟
	int random() default 5;
}
