package com.atguigu.gmall.pms.dao;

import com.atguigu.gmall.pms.entity.ProductAttrValueEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * spu属性值
 * 
 * @author Asuna
 * @email 624261289@qq.com
 * @date 2020-04-03 13:58:53
 */
@Mapper
public interface ProductAttrValueDao extends BaseMapper<ProductAttrValueEntity> {

	List<ProductAttrValueEntity> querySearchAttrValueBySpuId(Long spuId);
}
