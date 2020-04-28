package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.search.config.HighLightResultMapper;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParam;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVO;
import com.atguigu.gmall.search.pojo.SearchResponseVO;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.*;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {

	@Autowired
	private ElasticsearchRestTemplate restTemplate;

	@Autowired
	private HighLightResultMapper resultMapper;

	public SearchResponseVO search(SearchParam searchParam) {
		AggregatedPage<Goods> goodsAggregatedPage = this.restTemplate.queryForPage(this.buildSearchQuery(searchParam), Goods.class, resultMapper);
		SearchResponseVO responseVO = this.parseAggResult(goodsAggregatedPage);
		responseVO.setProducts(goodsAggregatedPage.getContent());
		responseVO.setTotal(goodsAggregatedPage.getTotalElements());
		responseVO.setPageNum(searchParam.getPageNum());
		responseVO.setPageSize(searchParam.getPageSize());
		return responseVO;
	}

	private SearchResponseVO parseAggResult(AggregatedPage<Goods> goodsAggregatedPage) {
		SearchResponseVO responseVO = new SearchResponseVO();
		//解析品牌聚合结果集
		SearchResponseAttrVO brand = new SearchResponseAttrVO();
		brand.setName("品牌");
		//获取品牌的聚合结果集
		ParsedLongTerms brandIdAgg = (ParsedLongTerms) goodsAggregatedPage.getAggregation("brandIdAgg");
		List<String> brandValues = brandIdAgg.getBuckets().stream().map(bucket -> {
			Map<String, String> map = new HashMap<>();
			// 获取品牌ID
			map.put("id", bucket.getKeyAsString());
			// 获取品牌名称，从子聚合查询
			Map<String, Aggregation> brandIdSubMap = bucket.getAggregations().asMap();
			ParsedStringTerms brandNameAgg = (ParsedStringTerms) brandIdSubMap.get("brandNameAgg");
			String brandName = brandNameAgg.getBuckets().get(0).getKeyAsString();
			map.put("name", brandName);
			return JSONObject.toJSONString(map);
		}).collect(Collectors.toList());
		brand.setValue(brandValues);
		responseVO.setBrand(brand);

		//解析分类聚合结果集
		SearchResponseAttrVO category = new SearchResponseAttrVO();
		category.setName("分类");
		//获取分类的聚合结果集
		ParsedLongTerms categoryIdAgg = (ParsedLongTerms) goodsAggregatedPage.getAggregation("categoryIdAgg");
		List<String> categoryValues = categoryIdAgg.getBuckets().stream().map(bucket -> {
			Map<String, String> map = new HashMap<>();
			// 获取分类ID
			map.put("id", bucket.getKeyAsString());
			// 获取分类名称，从子聚合查询
			ParsedStringTerms categoryNameAgg = bucket.getAggregations().get("categoryNameAgg");
			String categoryName = categoryNameAgg.getBuckets().get(0).getKeyAsString();
			map.put("name", categoryName);
			return JSONObject.toJSONString(map);
		}).collect(Collectors.toList());
		category.setValue(categoryValues);
		responseVO.setCatelog(category);

		// 解析规格参数
		// 获取嵌套聚合对象
		ParsedNested attrAgg = (ParsedNested) goodsAggregatedPage.getAggregation("attrAgg");
		// 规格参数id聚合对象
		ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
		List<Terms.Bucket> buckets = (List<Terms.Bucket>) attrIdAgg.getBuckets();
		if (!CollectionUtils.isEmpty(buckets)) {
			List<SearchResponseAttrVO> searchResponseAttrVO = buckets.stream().map(bucket -> {
				SearchResponseAttrVO responseAttrVO = new SearchResponseAttrVO();
				// 设置规格参数id
				responseAttrVO.setProductAttributeId(bucket.getKeyAsNumber().longValue());
				// 设置规格参数名称（例:内存）
				List<? extends Terms.Bucket> nameBuckets = ((ParsedStringTerms) (bucket.getAggregations().get("attrNameAgg"))).getBuckets();
				responseAttrVO.setName(nameBuckets.get(0).getKeyAsString());
				// 设置规格参数Value(例:8GB 16GB 32GB)
				List<? extends Terms.Bucket> valueBuckets = ((ParsedStringTerms) (bucket.getAggregations().get("attrValueAgg"))).getBuckets();
				List<String> values = valueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
				responseAttrVO.setValue(values);
				return responseAttrVO;
			}).collect(Collectors.toList());
			responseVO.setAttrs(searchResponseAttrVO);
		}

		return responseVO;
	}

	public SearchQuery buildSearchQuery(SearchParam searchParam) {
		// 1.构建查询
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
		String keyword = searchParam.getKeyword();
		String[] brand = searchParam.getBrand();
		String[] category = searchParam.getCatelog3();
		String[] props = searchParam.getProps();
		Integer priceFrom = searchParam.getPriceFrom();
		Integer priceTo = searchParam.getPriceTo();
		if (StringUtils.isBlank(keyword)) {
			return null;
		}
		//构建布尔查询
		BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));
		//构建品牌过滤
		if (brand != null && brand.length != 0) {
			boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brand));
		}
		//构建分类过滤
		if (category != null && category.length != 0) {
			boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", category));
		}
		//构建规格参数过滤
		if (props != null && props.length != 0) {
			for (String prop : props) {
				BoolQueryBuilder propQueryBuilder = QueryBuilders.boolQuery();
				String[] split = StringUtils.split(prop, ":");
				if (split == null || split.length != 2) {
					continue;
				}
				String[] attrValues = StringUtils.split(split[1], "-");
				BoolQueryBuilder nestedQueryBuilder = QueryBuilders.boolQuery();
				nestedQueryBuilder.must(QueryBuilders.termQuery("attrs.attrId", split[0]));
				nestedQueryBuilder.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));
				propQueryBuilder.must(QueryBuilders.nestedQuery("attrs", nestedQueryBuilder, ScoreMode.None));
				boolQueryBuilder.filter(propQueryBuilder);
			}
		}
		//构建价格区间过滤
		RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("price");
		if (priceFrom != null) {
			rangeQueryBuilder.gte(priceFrom);
		}
		if (priceTo != null) {
			rangeQueryBuilder.lte(priceTo);
		}
		boolQueryBuilder.filter(rangeQueryBuilder);
		queryBuilder.withQuery(boolQueryBuilder);

		// 2.构建分页
		Integer pageNum = searchParam.getPageNum();
		Integer pageSize = searchParam.getPageSize();
		queryBuilder.withPageable(PageRequest.of(pageNum - 1, pageSize));

		// 3.构建排序
		String order = searchParam.getOrder();
		if (!StringUtils.isEmpty(order)) {
			String[] orders = StringUtils.split(order, ":");
			if (orders != null && orders.length == 2) {
				String field = null;
				switch (orders[0]) {
					case "1":
						field = "sale";
						break;
					case "2":
						field = "price";
						break;
				}
				queryBuilder.withSort(SortBuilders.fieldSort(field).order(StringUtils.equals("asc", orders[1]) ? SortOrder.ASC : SortOrder.DESC));
			}
		}

		// 4.构建高亮
		queryBuilder.withHighlightBuilder(new HighlightBuilder().field("title").preTags("<em>").postTags("</em>"));

		// 5.构建聚合
		// 5.1 品牌聚合
		// 5.1.1 品牌聚合
		TermsAggregationBuilder brandAggregationBuilder = AggregationBuilders.terms("brandIdAgg").field("brandId");
		// 5.1.2 品牌子聚合
		brandAggregationBuilder.subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"));
		queryBuilder.addAggregation(brandAggregationBuilder);

		// 5.2 分类聚合
		// 5.2.1 分类聚合
		TermsAggregationBuilder cateAggregationBuilder = AggregationBuilders.terms("categoryIdAgg").field("categoryId");
		// 5.2.2 分类子聚合
		cateAggregationBuilder.subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName"));
		queryBuilder.addAggregation(cateAggregationBuilder);

		// 5.3 搜索的规格属性聚合
		queryBuilder.addAggregation(AggregationBuilders.nested("attrAgg", "attrs")
				.subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
						.subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
						.subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

		// 6.结果集过滤
		queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{"skuId", "pic", "title", "price"}, null));

		// 7.返回SearchQuery对象
		return queryBuilder.build();
	}
}
