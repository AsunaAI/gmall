package com.atguigu.gmall.item.service;

import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.sms.vo.SaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemService {

	@Autowired
	private GmallPmsClient gmallPmsClient;

	@Autowired
	private GmallSmsClient gmallSmsClient;

	@Autowired
	private GmallWmsClient gmallWmsClient;

	@Autowired
	private ThreadPoolExecutor threadPoolExecutor;

	public ItemVo queryItemVo(Long skuId) {
		ItemVo itemVo = new ItemVo();
		itemVo.setSkuId(skuId);
		// 根据skuId查询sku数据
		CompletableFuture<Object> skuCompletableFuture = CompletableFuture.supplyAsync(() -> {
			Resp<SkuInfoEntity> skuResp = this.gmallPmsClient.querySkuById(skuId);
			SkuInfoEntity skuInfoEntity = skuResp.getData();
			if (skuInfoEntity == null) {
				return itemVo;
			}
			itemVo.setSkuTitle(skuInfoEntity.getSkuTitle());
			itemVo.setSkuSubtitle(skuInfoEntity.getSkuSubtitle());
			itemVo.setPrice(skuInfoEntity.getPrice());
			itemVo.setWeight(skuInfoEntity.getWeight());
			itemVo.setSpuId(skuInfoEntity.getSpuId());
			return skuInfoEntity;
		}, threadPoolExecutor);
		// 根据sku中的spuid查询spu信息
		CompletableFuture<Void> spuCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
			Resp<SpuInfoEntity> spuResp = this.gmallPmsClient.querySpuById(((SkuInfoEntity) sku).getSpuId());
			SpuInfoEntity spuInfoEntity = spuResp.getData();
			if (spuInfoEntity != null) {
				itemVo.setSpuName(spuInfoEntity.getSpuName());
			}
		}, threadPoolExecutor);
		// 根据skuId查询sku图片列表
		CompletableFuture<Void> imageCompletableFuture = CompletableFuture.runAsync(() -> {
			Resp<List<SkuImagesEntity>> images = this.gmallPmsClient.querySkuImagesBySkuId(skuId);
			List<SkuImagesEntity> imagesData = images.getData();
			itemVo.setPics(imagesData);
		}, threadPoolExecutor);
		// 根据sku中的brandId查询品牌
		CompletableFuture<Void> brandCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
			Resp<BrandEntity> brandEntityResp = this.gmallPmsClient.queryBrandById(((SkuInfoEntity) sku).getBrandId());
			BrandEntity brandEntity = brandEntityResp.getData();
			itemVo.setBrandEntity(brandEntity);
		}, threadPoolExecutor);
		// 根据sku中的categoryId查询分类
		CompletableFuture<Void> cateCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
			Resp<CategoryEntity> categoryEntityResp = this.gmallPmsClient.queryCategoryById(((SkuInfoEntity) sku).getCatalogId());
			CategoryEntity categoryEntity = categoryEntityResp.getData();
			itemVo.setCategoryEntity(categoryEntity);
		}, threadPoolExecutor);
		// 根据skuId查询营销信息
		CompletableFuture<Void> saleCompletableFuture = CompletableFuture.runAsync(() -> {
			Resp<List<SaleVo>> querySalesBySkuId = this.gmallSmsClient.querySalesBySkuId(skuId);
			List<SaleVo> salesBySkuIdData = querySalesBySkuId.getData();
			itemVo.setSales(salesBySkuIdData);
		}, threadPoolExecutor);
		// 根据skuId查询库存信息
		CompletableFuture<Void> storeCompletableFuture = CompletableFuture.runAsync(() -> {
			Resp<List<WareSkuEntity>> wareSkuBySkuId = this.gmallWmsClient.queryWareSkusBySkuId(skuId);
			List<WareSkuEntity> wareSkuBySkuIdData = wareSkuBySkuId.getData();
			itemVo.setStore(wareSkuBySkuIdData.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
		}, threadPoolExecutor);
		// 根据spuId查询所有skuId，再查询所有sku的销售属性
		CompletableFuture<Void> saleAttrCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
			Resp<List<SkuSaleAttrValueEntity>> salesAttrResp = this.gmallPmsClient.queryAttrValueEntities(((SkuInfoEntity) sku).getSpuId());
			List<SkuSaleAttrValueEntity> saleAttrValueEntities = salesAttrResp.getData();
			itemVo.setSaleAttrs(saleAttrValueEntities);
		}, threadPoolExecutor);
		// 根据spuId查询海报
		CompletableFuture<Void> descAttrCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
			Resp<SpuInfoDescEntity> spuInfoDescEntityResp = this.gmallPmsClient.querySpuDesc(((SkuInfoEntity) sku).getSpuId());
			SpuInfoDescEntity descEntity = spuInfoDescEntityResp.getData();
			if (descEntity != null) {
				String descript = descEntity.getDecript();
				String[] desc = StringUtils.split(descript, ",");
				itemVo.setImages(Arrays.asList(desc));
			}
		}, threadPoolExecutor);
		// 根据分类id和spuId查询组和组下的规格参数 带值
		CompletableFuture<Void> groupAttrCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
			Resp<List<ItemGroupVo>> attrValueResp = this.gmallPmsClient.queryItemGroupVoByCidAndSpuId(((SkuInfoEntity) sku).getCatalogId(), ((SkuInfoEntity) sku).getSpuId());
			List<ItemGroupVo> itemGroupVos = attrValueResp.getData();
			itemVo.setAttrGroups(itemGroupVos);
		}, threadPoolExecutor);
		CompletableFuture.allOf(spuCompletableFuture, imageCompletableFuture, brandCompletableFuture,
				cateCompletableFuture, saleCompletableFuture, storeCompletableFuture,
				saleAttrCompletableFuture, descAttrCompletableFuture, groupAttrCompletableFuture).join();
		return itemVo;
	}
}
