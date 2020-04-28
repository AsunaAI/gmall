package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.index.annotation.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.vo.CategoryVO;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

	@Autowired
	private GmallPmsClient gmallPmsClient;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private RedissonClient redissonClient;

	private static final String KEY_PREFIX = "index:cates:";

	public List<CategoryEntity> queryLevel1Category() {
		Resp<List<CategoryEntity>> listResp = this.gmallPmsClient.queryCategoriesByPidOrLevel(1, null);
		return listResp.getData();
	}

	@GmallCache(prefix = "index:cates:", timeout = 7200, random = 100)
	public List<CategoryVO> querySubCategory(Long pid) {
//		String cateJson = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
//		if (StringUtils.isNotBlank(cateJson)) {
//			return JSON.parseArray(cateJson, CategoryVO.class);
//		}
//		RLock lock = this.redissonClient.getLock("lock" + pid);
//		lock.lock();
//		String cateJson2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
//		if (StringUtils.isNotBlank(cateJson2)) {
//			lock.unlock();
//			return JSON.parseArray(cateJson2, CategoryVO.class);
//		}
		Resp<List<CategoryVO>> listResp = this.gmallPmsClient.querySubCategory(pid);
		List<CategoryVO> categoryVOS = listResp.getData();
//		this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryVOS), 7 + new Random().nextInt(5), TimeUnit.DAYS);
//		lock.unlock();
		return categoryVOS;
	}
}
