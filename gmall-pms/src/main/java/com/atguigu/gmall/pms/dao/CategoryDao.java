package com.atguigu.gmall.pms.dao;

import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.vo.CategoryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商品三级分类
 * 
 * @author Asuna
 * @email 624261289@qq.com
 * @date 2020-04-03 13:58:53
 */
@Mapper
public interface CategoryDao extends BaseMapper<CategoryEntity> {

	List<CategoryVO> querySubCategory(Long pid);
}
