package com.atguigu.gmall.sms.service.impl;

import com.atguigu.gmall.sms.dao.SkuFullReductionDao;
import com.atguigu.gmall.sms.dao.SkuLadderDao;
import com.atguigu.gmall.sms.entity.SkuFullReductionEntity;
import com.atguigu.gmall.sms.entity.SkuLadderEntity;
import com.atguigu.gmall.sms.vo.SaleVo;
import com.atguigu.gmall.sms.vo.SkuSaleVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.sms.dao.SkuBoundsDao;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;
import com.atguigu.gmall.sms.service.SkuBoundsService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("skuBoundsService")
public class SkuBoundsServiceImpl extends ServiceImpl<SkuBoundsDao, SkuBoundsEntity> implements SkuBoundsService {

    @Autowired
    private SkuFullReductionDao skuFullReductionDao;

    @Autowired
    private SkuLadderDao skuLadderDao;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<SkuBoundsEntity> page = this.page(
                new Query<SkuBoundsEntity>().getPage(params),
                new QueryWrapper<SkuBoundsEntity>()
        );

        return new PageVo(page);
    }

    @Override
    @Transactional
    public void saveSkuSaleInfo(SkuSaleVO skuSaleVO) {
        // 3.1. 积分优惠
        SkuBoundsEntity skuBoundsEntity = new SkuBoundsEntity();
        BeanUtils.copyProperties(skuSaleVO, skuBoundsEntity);
        // 数据库保存的是整数0-15，页面绑定是0000-1111
        List<Integer> work = skuSaleVO.getWork();
        if (!CollectionUtils.isEmpty(work)){
            skuBoundsEntity.setWork(work.get(0) * 8 + work.get(1) * 4 + work.get(2) * 2 + work.get(3));
        }
        this.save(skuBoundsEntity);

        // 3.2. 满减优惠
        SkuFullReductionEntity skuFullReductionEntity = new SkuFullReductionEntity();
        BeanUtils.copyProperties(skuSaleVO, skuFullReductionEntity);
        skuFullReductionEntity.setAddOther(skuSaleVO.getFullAddOther());
        this.skuFullReductionDao.insert(skuFullReductionEntity);

        // 3.3. 数量折扣
        SkuLadderEntity skuLadderEntity = new SkuLadderEntity();
        BeanUtils.copyProperties(skuSaleVO, skuLadderEntity);
        skuLadderEntity.setAddOther(skuSaleVO.getLadderAddOther());
        this.skuLadderDao.insert(skuLadderEntity);
    }

    @Override
    public List<SaleVo> querySalesBySkuId(Long skuId) {
        List<SaleVo> saleVos= new ArrayList<>();
        //查询积分信息
        SkuBoundsEntity skuBoundsEntity = this.getOne(new QueryWrapper<SkuBoundsEntity>().eq("sku_id", skuId));
        if (skuBoundsEntity != null) {
            SaleVo boundsVo = new SaleVo();
            boundsVo.setType("积分");
            StringBuffer desc = new StringBuffer();
            if (skuBoundsEntity.getGrowBounds() != null && skuBoundsEntity.getGrowBounds() .intValue() > 0) {
                desc.append("成长积分送" + skuBoundsEntity.getGrowBounds()+ "点！ ");
            }
            if (skuBoundsEntity.getBuyBounds() != null && skuBoundsEntity.getBuyBounds().intValue() > 0) {
                desc.append("购物积分送" + skuBoundsEntity.getBuyBounds() + "点！");
            }
            boundsVo.setDesc(desc.toString());
            saleVos.add(boundsVo);
        }


        //查询打折信息
        SkuLadderEntity skuLadderEntity = this.skuLadderDao.selectOne(new QueryWrapper<SkuLadderEntity>().eq("sku_id", skuId));
        if (skuLadderEntity != null) {
            SaleVo ladderVo = new SaleVo();
            ladderVo.setType("打折");
            ladderVo.setDesc("本商品满" + skuLadderEntity.getFullCount() + "件，打" + skuLadderEntity.getDiscount().divide(new BigDecimal(10)) + "折！");
            saleVos.add(ladderVo);
        }


        //查询满减信息
        SkuFullReductionEntity skuFullReductionEntity = this.skuFullReductionDao.selectOne(new QueryWrapper<SkuFullReductionEntity>().eq("sku_id", skuId));
        if (skuFullReductionEntity != null) {
            SaleVo fullReductionVo = new SaleVo();
            fullReductionVo.setType("满减");
            fullReductionVo.setDesc("本商品满" + skuFullReductionEntity.getFullPrice() + "减" + skuFullReductionEntity.getReducePrice());
            saleVos.add(fullReductionVo);
        }
        return saleVos;
    }

}