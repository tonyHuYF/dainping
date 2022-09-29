package com.tony.dainping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tony.dainping.dto.Result;
import com.tony.dainping.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);
}
