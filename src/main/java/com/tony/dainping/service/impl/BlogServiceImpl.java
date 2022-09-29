package com.tony.dainping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tony.dainping.dto.Result;
import com.tony.dainping.dto.UserDTO;
import com.tony.dainping.entity.Blog;
import com.tony.dainping.entity.Follow;
import com.tony.dainping.entity.User;
import com.tony.dainping.mapper.BlogMapper;
import com.tony.dainping.service.IBlogService;
import com.tony.dainping.service.IFollowService;
import com.tony.dainping.service.IUserService;
import com.tony.dainping.utils.SystemConstants;
import com.tony.dainping.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:liked:" + id;
        //查top5
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());

        String idsStr = StrUtil.join(",", ids);

        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("ORDER BY FIELD(id," + idsStr + ")").list()
                .stream()
                .map(p -> BeanUtil.copyProperties(p, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();

        if (user == null) {
            return;
        }

        Long userId = user.getId();
        String key = "blog:liked:" + blog.getId();
        //判断是否点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        blog.setIsLike(score != null);
    }


    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked:" + id;
        //判断是否点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            //未点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //已点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);

        if (!isSuccess) {
            return Result.fail("笔记新增失败");
        }

        //查关注用户的粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();

        follows.forEach(p -> {
            //推送给粉丝的收件箱
            String key = "feed:" + p.getUserId().toString();
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        });


        // 返回id
        return Result.ok(blog.getId());
    }
}
