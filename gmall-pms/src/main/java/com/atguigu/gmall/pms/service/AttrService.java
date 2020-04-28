package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.vo.AttrVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;


/**
 * 商品属性
 *
 * @author Asuna
 * @email 624261289@qq.com
 * @date 2020-04-03 13:58:53
 */
public interface AttrService extends IService<AttrEntity> {

    PageVo queryPage(QueryCondition params);

	PageVo queryByCidTypePage(QueryCondition queryCondition, Long cid, Integer type);

	void saveAttrVO(AttrVO attrVO);
}

