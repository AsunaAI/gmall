package com.atguigu.gmall.sms.service;

import com.atguigu.gmall.sms.vo.SaleVo;
import com.atguigu.gmall.sms.vo.SkuSaleVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;

import java.util.List;


/**
 * 商品sku积分设置
 *
 * @author Asuna
 * @email 624261289@qq.com
 * @date 2020-04-03 16:13:50
 */
public interface SkuBoundsService extends IService<SkuBoundsEntity> {

    PageVo queryPage(QueryCondition params);

	void saveSkuSaleInfo(SkuSaleVO skuSaleVO);

	List<SaleVo> querySalesBySkuId(Long skuId);
}

