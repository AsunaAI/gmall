package com.atguigu.gmall.oms.dao;

import com.atguigu.gmall.oms.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单
 * 
 * @author Asuna
 * @email 624261289@qq.com
 * @date 2020-04-28 16:55:49
 */
@Mapper
public interface OrderDao extends BaseMapper<OrderEntity> {

    int closeOrder(String orderToken);

    int payOrder(String orderToken);
}
