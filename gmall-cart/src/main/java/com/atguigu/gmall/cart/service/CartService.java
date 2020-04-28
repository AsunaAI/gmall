package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptors.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.sms.vo.SaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private GmallPmsClient gmallPmsClient;

    @Autowired
    private GmallSmsClient gmallSmsClient;

    @Autowired
    private GmallWmsClient gmallWmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "gmall:cart:";

    private static final String PRICE_PREFIX = "gmall:sku:";

    public void addCart(Cart cart) {
        String key = getLoginStatus();
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        String skuId = cart.getSkuId().toString();
        if (hashOps.hasKey(skuId)) {
            Integer count = cart.getCount();
            String cartJson = hashOps.get(skuId).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount() + count);
        } else {
            cart.setCheck(true);
            Resp<SkuInfoEntity> skuInfoEntityResp = this.gmallPmsClient.querySkuById(cart.getSkuId());
            SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
            if (skuInfoEntity != null) {
                cart.setDefaultImage(skuInfoEntity.getSkuDefaultImg());
                cart.setPrice(skuInfoEntity.getPrice());
                cart.setTitle(skuInfoEntity.getSkuTitle());
            }
            Resp<List<SkuSaleAttrValueEntity>> saleAttrValueResp = this.gmallPmsClient.queryAttrValueBySkuId(cart.getSkuId());
            List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = saleAttrValueResp.getData();
            cart.setSaleAttrValues(skuSaleAttrValueEntities);
            Resp<List<SaleVo>> saleResp = this.gmallSmsClient.querySalesBySkuId(cart.getSkuId());
            List<SaleVo> saleVos = saleResp.getData();
            cart.setSales(saleVos);
            Resp<List<WareSkuEntity>> wareResp = this.gmallWmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResp.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
            }
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId, skuInfoEntity.getPrice().toString());
        }
        hashOps.put(skuId, JSON.toJSONString(cart));
    }

    private String getLoginStatus() {
        String key = KEY_PREFIX;
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        if (userInfo.getId() == null) {
            key += userInfo.getUserKey();
        } else {
            key += userInfo.getId();
        }
        return key;
    }

    public List<Cart> queryCarts() {
        // 获取登录信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        // 查询未登录状态购物车
        String unloginKey = KEY_PREFIX + userInfo.getUserKey();
        BoundHashOperations<String, Object, Object> unloginHashOps = this.redisTemplate.boundHashOps(unloginKey);
        List<Object> unloginCartJsons = unloginHashOps.values();
        List<Cart> unloginCarts = null;
        if (!CollectionUtils.isEmpty(unloginCartJsons)) {
            unloginCarts = unloginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                String priceString = this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId());
                cart.setCurrentPrice(new BigDecimal(priceString));
                return cart;
            }).collect(Collectors.toList());
        }
        // 判断是否登录
        if (userInfo.getId() == null) {
            // 没有登录，直接返回未登录状态购物车
            return unloginCarts;
        } else {
            // 登录了，先合并购物车，再查询
            String loginKey = KEY_PREFIX + userInfo.getId();
            BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(loginKey);
            if (!CollectionUtils.isEmpty(unloginCarts)) {
                unloginCarts.forEach(cart -> {
                    if (loginHashOps.hasKey(cart.getSkuId().toString())) {
                        Integer count = cart.getCount();
                        String cartJson = loginHashOps.get(cart.getSkuId().toString()).toString();
                        cart = JSON.parseObject(cartJson, Cart.class);
                        cart.setCount(cart.getCount() + count);
                    }
                    loginHashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
                });
                this.redisTemplate.delete(unloginKey);
            }
            List<Object> loginCartJsons = loginHashOps.values();
            if (!CollectionUtils.isEmpty(loginCartJsons)) {
                return loginCartJsons.stream().map(cartJson -> {
                    Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                    String priceString = this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId());
                    cart.setCurrentPrice(new BigDecimal(priceString));
                    return cart;
                }).collect(Collectors.toList());
            }
            return null;
        }
    }

    public void updateCart(Cart cart) {
        String key = this.getLoginStatus();
        // 获取购物车
        BoundHashOperations<String, Object, Object> boundHashOps = redisTemplate.boundHashOps(key);
        Integer count = cart.getCount();
        // 判断请求修改的这条购物车信息
        if (boundHashOps.hasKey(cart.getSkuId().toString())) {
            String cartJson = boundHashOps.get(cart.getSkuId().toString()).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(count);
            boundHashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        }
    }

    public void deleteCart(Long skuId) {
        String key = this.getLoginStatus();
        // 删除购物车
        BoundHashOperations<String, Object, Object> boundHashOps = redisTemplate.boundHashOps(key);
        if(boundHashOps.hasKey(skuId.toString())) {
            boundHashOps.delete(skuId.toString());
        }
    }
}
