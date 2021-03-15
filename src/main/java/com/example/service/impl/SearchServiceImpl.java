package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.entity.Post;
import com.example.search.model.PostDocment;
import com.example.search.mq.PostMqIndexMessage;
import com.example.search.repository.PostRepository;
import com.example.service.PostService;
import com.example.service.SearchService;
import com.example.vo.PostVo;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.*;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    PostRepository postRepository;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    PostService postService;

    @Override
    public IPage search(Page page, String keyword) {
        // 分页信息 mybatis plus的page 转成 jpa的page
        Long current = page.getCurrent() - 1;
        Long size = page.getSize();
        Pageable pageable = PageRequest.of(current.intValue(), size.intValue());

        // 搜索es得到pageData
        /*MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword,
                "title", "authorName", "categoryName");*/
        /*
        * 这边只有一个查询条件,如果有多个需要使用QueryBuilders.boolQuery().must(queryBuilder1).should(queryBuilder2)....
        * .must相当于and：文档必须匹配must查询条件
        * .mustNot子句：文档不能匹配该查询条件
        * .should相当于or：文档应该匹配should子句查询的一个或多个
        * .minimumShouldMatch:该参数控制一个文档必须匹配的should子查询的数量，如果不设置参数minimum_should_match，其默认值是0。建议在布尔查询中，显示设置参数minimum_should_match的值。
        * .filter：过滤器，文档必须匹配该过滤条件，跟must子句的唯一区别是，filter不影响查询的score
        *
        * .term是代表完全匹配，即不进行分词器分析，文档中必须包含整个搜索的词汇
        * .match和.term的区别是,match查询的时候,elasticsearch会根据你给定的字段提供合适的分析器,而term查询不会有分析器分析的过程
        * .match_all查询指定索引下的所有文档
        * .match_phrase:短语查询, 搜索的文档必须包含所有短语的分词结果而且顺序一致
        * .multi_match:可以指定多个字段
        * .rangeQuery:范围查询,一般配合.gt/e和.lt/e使用
        * .wildcard 模糊查询:?匹配单个字符，*匹配多个字符
        * */
        // org.springframework.data.domain.Page<PostDocment> docments = postRepository.search(multiMatchQueryBuilder, pageable);

        //修改为模糊匹配,并支持大小写搜索(分词器默认是全小写的)
        keyword = keyword.toLowerCase();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                .should(QueryBuilders.wildcardQuery("title","*" + keyword + "*"))
                .should(QueryBuilders.wildcardQuery("authorName","*" + keyword + "*"))
                .should(QueryBuilders.wildcardQuery("categoryName","*" + keyword + "*"))
                .should(QueryBuilders.fuzzyQuery("content",keyword))
                .minimumShouldMatch(1);
        org.springframework.data.domain.Page<PostDocment> docments = postRepository.search(boolQueryBuilder, pageable);

        // 结果信息 jpa的pageData转成mybatis plus的pageData
        IPage pageData = new Page(page.getCurrent(), page.getSize(), docments.getTotalElements());
        pageData.setRecords(docments.getContent());
        return pageData;
    }

    @Override
    public int initEsData(List<PostVo> records) {
        if(records == null || records.isEmpty()) {
            return 0;
        }

        List<PostDocment> documents = new ArrayList<>();
        for(PostVo vo : records) {
            // 映射转换
            PostDocment postDocment = modelMapper.map(vo, PostDocment.class);
            documents.add(postDocment);
        }
        postRepository.saveAll(documents);
        return documents.size();
    }

    @Override
    public void createOrUpdateIndex(PostMqIndexMessage message) {
        Long postId = message.getPostId();
        PostVo postVo = postService.selectOnePost(new QueryWrapper<Post>().eq("p.id", postId));

        PostDocment postDocment = modelMapper.map(postVo, PostDocment.class);

        postRepository.save(postDocment);

        log.info("es 索引更新成功！ ---> {}", postDocment.toString());
    }

    @Override
    public void removeIndex(PostMqIndexMessage message) {
        Long postId = message.getPostId();

        postRepository.deleteById(postId);
        log.info("es 索引删除成功！ ---> {}", message.toString());
    }
}
