package com.atguigu.gmall.wms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;


/**
 * 商品库存
 *
 * @author Asuna
 * @email 624261289@qq.com
 * @date 2020-04-08 14:48:56
 */
public interface WareSkuService extends IService<WareSkuEntity> {

    PageVo queryPage(QueryCondition params);
}

