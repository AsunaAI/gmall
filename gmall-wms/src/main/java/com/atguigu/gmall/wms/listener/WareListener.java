package com.atguigu.gmall.wms.listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.wms.dao.WareSkuDao;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WareListener {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private WareSkuDao wareSkuDao;

    private static final String KEY_PREFIX = "stock:lock:";

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "stock-unlock-queue", durable = "true"),
            exchange = @Exchange(value = "GMALL_ORDER_EXCHANGE", type = ExchangeTypes.TOPIC, ignoreDeclarationExceptions = "true"),
            key = {"stock.unlock"}
    ))
    public void unlockListener(String orderToken) {
        String lockJson = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        if (StringUtils.isBlank(lockJson)) {
            return;
        }
        List<SkuLockVo> lockVos = JSON.parseArray(lockJson, SkuLockVo.class);
        lockVos.forEach(skuLockVo -> {
            this.wareSkuDao.unLockStore(skuLockVo.getWareSkuId(), skuLockVo.getCount());
        });
        this.redisTemplate.delete(KEY_PREFIX + orderToken);
    }

    // 减库存
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "stock-minus-queue", durable = "true"),
            exchange = @Exchange(value = "GMALL_ORDER_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"stock.minus"}
    ))
    public void minusStoreListener(String orderToken) {
        String lockJson = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        List<SkuLockVo> lockVos = JSONObject.parseArray(lockJson, SkuLockVo.class);
        lockVos.forEach(skuLockVo -> {
            this.wareSkuDao.minusStore(skuLockVo.getWareSkuId(), skuLockVo.getCount());
        });
    }
}
