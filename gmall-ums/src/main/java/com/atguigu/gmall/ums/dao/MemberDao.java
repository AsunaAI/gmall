package com.atguigu.gmall.ums.dao;

import com.atguigu.gmall.ums.entity.MemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员
 * 
 * @author Asuna
 * @email 624261289@qq.com
 * @date 2020-04-23 18:02:54
 */
@Mapper
public interface MemberDao extends BaseMapper<MemberEntity> {
	
}
