package com.atguigu.gmall.index.aspect;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.index.annotation.GmallCache;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class GmallCacheAspect {

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private RedissonClient redissonClient;

	@Around("@annotation(com.atguigu.gmall.index.annotation.GmallCache)")
	public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
		Object result = null;
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		Class<?> returnType = method.getReturnType();
		GmallCache gmallCache = method.getAnnotation(GmallCache.class);
		String prefix = gmallCache.prefix();
		Object[] args = joinPoint.getArgs();
		String key = prefix + Arrays.asList(args);
		//判断缓存中是否有，有的话直接返回
		result = this.cacheHit(returnType, key);
		if (result != null) {
			return result;
		}
		//缓存中没有，加分布式锁
		RLock lock = this.redissonClient.getLock("lock" + Arrays.asList(args));
		lock.lock();
		//再次判断缓存中是否有，有的话直接返回
		result = this.cacheHit(returnType, key);
		if (result != null) {
			lock.unlock();
			return result;
		}
		//缓存中没有，执行目标方法查询数据库
		result = joinPoint.proceed(args);
		//将数据库查询结果放入缓存中
		int timeout = gmallCache.timeout();
		int random = gmallCache.random();
		this.redisTemplate.opsForValue().set(key, JSON.toJSONString(result), timeout + (int) (Math.random() * random), TimeUnit.MINUTES);
		lock.unlock();
		return result;
	}

	private Object cacheHit(Class<?> returnType, String key) {
		String json = this.redisTemplate.opsForValue().get(key);
		if (StringUtils.isNotBlank(json)) {
			return JSON.parseObject(json, returnType);
		}
		return null;
	}
}
