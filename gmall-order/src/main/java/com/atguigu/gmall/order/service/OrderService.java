package com.atguigu.gmall.order.service;

import com.atguigu.core.bean.Resp;
import com.atguigu.core.bean.UserInfo;
import com.atguigu.core.exception.OrderException;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptors.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.sms.vo.SaleVo;
import com.atguigu.gmall.ums.entity.MemberEntity;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallCartClient cartClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    private static final String TOKEN_PREFIX = "order:token:";

    public OrderConfirmVo confirm() {
        OrderConfirmVo orderConfirmVo = new OrderConfirmVo();
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getId();
        if (userId == null) {
            return null;
        }
        // 获取用户收货地址列表 》根据用户id查询收货地址列表
        CompletableFuture<Void> addressCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<List<MemberReceiveAddressEntity>> addressesResp = this.umsClient.queryAddressesByUserId(userId);
            List<MemberReceiveAddressEntity> memberReceiveAddressEntities = addressesResp.getData();
            orderConfirmVo.setAddresses(memberReceiveAddressEntities);
        }, threadPoolExecutor);
        // 获取购物车选中的商品信息 >> skuId & count
        CompletableFuture<Void> cartCompletableFuture = CompletableFuture.supplyAsync(() -> {
            Resp<List<Cart>> cartsResp = this.cartClient.queryCheckedCartsByUserId(userId);
            List<Cart> carts = cartsResp.getData();
            if (CollectionUtils.isEmpty(carts)) {
                throw new OrderException("请勾选购物车商品！");
            }
            return carts;
        }, threadPoolExecutor).thenAcceptAsync(carts -> {
            List<OrderItemVo> itemVos = carts.stream().map(cart -> {
                Long skuId = cart.getSkuId();
                OrderItemVo orderItemVo = new OrderItemVo();
                CompletableFuture<Void> skuCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(skuId);
                    SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
                    if (skuInfoEntity != null) {
                        orderItemVo.setSkuId(skuId);
                        orderItemVo.setCount(cart.getCount());
                        orderItemVo.setDefaultImage(skuInfoEntity.getSkuDefaultImg());
                        orderItemVo.setPrice(skuInfoEntity.getPrice());
                        orderItemVo.setTitle(skuInfoEntity.getSkuTitle());
                        orderItemVo.setWeight(skuInfoEntity.getWeight());
                    }
                }, threadPoolExecutor);
                CompletableFuture<Void> saleAttrCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<List<SkuSaleAttrValueEntity>> saleAttrListResp = this.pmsClient.queryAttrValueBySkuId(skuId);
                    List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = saleAttrListResp.getData();
                    orderItemVo.setSaleAttrValues(skuSaleAttrValueEntities);
                }, threadPoolExecutor);
                CompletableFuture<Void> saleCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<List<SaleVo>> saleVoListResp = this.smsClient.querySalesBySkuId(skuId);
                    List<SaleVo> saleVos = saleVoListResp.getData();
                    orderItemVo.setSales(saleVos);
                }, threadPoolExecutor);
                CompletableFuture<Void> wareCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<List<WareSkuEntity>> wareSkuListResp = this.wmsClient.queryWareSkusBySkuId(skuId);
                    List<WareSkuEntity> wareSkuEntities = wareSkuListResp.getData();
                    if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                        orderItemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
                    }
                }, threadPoolExecutor);
                CompletableFuture.allOf(skuCompletableFuture, saleAttrCompletableFuture, saleCompletableFuture, wareCompletableFuture).join();
                return orderItemVo;
            }).collect(Collectors.toList());
            orderConfirmVo.setOrderItems(itemVos);
        }, threadPoolExecutor);
        // 查询用户信息，获取积分
        CompletableFuture<Void> boundCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<MemberEntity> memberEntityResp = this.umsClient.queryMemberById(userId);
            MemberEntity memberEntity = memberEntityResp.getData();
            orderConfirmVo.setBounds(memberEntity.getIntegration());
        }, threadPoolExecutor);
        // 生成唯一标识，防止重复提交（响应页面&保存redis）
        CompletableFuture<Void> tokenCompletableFuture = CompletableFuture.runAsync(() -> {
            String orderToken = IdWorker.getIdStr();
            orderConfirmVo.setOrderToken(orderToken);
            this.redisTemplate.opsForValue().set(TOKEN_PREFIX + orderToken, orderToken);
        }, threadPoolExecutor);
        CompletableFuture.allOf(addressCompletableFuture, cartCompletableFuture, boundCompletableFuture, tokenCompletableFuture).join();
        return orderConfirmVo;
    }

    public OrderEntity submit(OrderSubmitVo submitVo) {
        // 获取user
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        // 获取orderToken
        String orderToken = submitVo.getOrderToken();
        // 防重复提交,查询redis中orderToken信息，有，则是第一次提交，并删除redis中的orderToken

        // 校验总价格，总价一致，放行
        List<OrderItemVo> orderItems = submitVo.getOrderItems(); //商品清单
        BigDecimal totalPrice = submitVo.getTotalPrice(); //商品总价
        // 获取实时总价
        BigDecimal currentTotalPrice = orderItems.stream().map(orderItem -> {
            Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(orderItem.getSkuId());
            SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
            if (skuInfoEntity != null) {
                return skuInfoEntity.getPrice().multiply(new BigDecimal(orderItem.getCount()));
            }
            return new BigDecimal(0);
        }).reduce((a, b) -> a.add(b)).get();
        // 判断实时总价和页面总价格
        if (currentTotalPrice.compareTo(totalPrice) != 0) {
            throw new OrderException("页面已过期，请刷新页面！");
        }
        // 校验库存,锁定库存，一次性提示所有库存不够的商品信息
        List<SkuLockVo> lockVos = orderItems.stream().map(orderItem -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(orderItem.getSkuId());
            skuLockVo.setCount(orderItem.getCount());
            skuLockVo.setOrderToken(orderToken);
            return skuLockVo;
        }).collect(Collectors.toList());
        Resp<Object> wareResp = this.wmsClient.checkAndLockStore(lockVos);
        if (wareResp.getCode() != 0) {
            throw new OrderException(wareResp.getMsg());
        }
        // 下单
        Resp<OrderEntity> orderEntityResp = null;
        try {
            submitVo.setUserId(userInfo.getId());
            orderEntityResp = this.omsClient.saveOrder(submitVo);
        } catch (Exception e) {
            e.printStackTrace();
            // 发送消息给wms，解锁库存
            this.amqpTemplate.convertAndSend("stock.unlock", orderToken);
            throw new OrderException("服务器错误,创建订单失败！");
        }
        // 删除购物车（消息队列）
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userInfo.getId());
        List<Long> skuIds = orderItems.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        map.put("skuIds", skuIds);
        this.amqpTemplate.convertAndSend("cart.delete", map);
        if (orderEntityResp != null) {
            return orderEntityResp.getData();
        }
        return null;
    }
}
