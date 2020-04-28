package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.vo.AttrGroupVO;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;

import java.util.List;


/**
 * 属性分组
 *
 * @author Asuna
 * @email 624261289@qq.com
 * @date 2020-04-03 13:58:53
 */
public interface AttrGroupService extends IService<AttrGroupEntity> {

    PageVo queryPage(QueryCondition params);

	PageVo queryByCidPage(Long cid, QueryCondition condition);

	AttrGroupVO queryById(Long gid);

	List<AttrGroupVO> queryByCid(Long cid);

	List<ItemGroupVo> queryItemGroupVoByCidAndSpuId(Long cid, Long spuId);
}

