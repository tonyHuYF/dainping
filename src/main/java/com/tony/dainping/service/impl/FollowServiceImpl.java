package com.tony.dainping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tony.dainping.dto.Result;
import com.tony.dainping.dto.UserDTO;
import com.tony.dainping.entity.Follow;
import com.tony.dainping.mapper.FollowMapper;
import com.tony.dainping.service.IFollowService;
import com.tony.dainping.service.IUserService;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;


    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long useId = UserHolder.getUser().getId();
        String key = "follow:users:" + useId;

        if (BooleanUtil.isTrue(isFollow)) {
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(useId);

            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            boolean isRemove = remove(new QueryWrapper<Follow>().eq("user_id", useId).eq("follow_user_id", followUserId));
            if (isRemove) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }

        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long useId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", useId).eq("follow_user_id", followUserId).count();

        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long useId = UserHolder.getUser().getId();

        String key1 = "follow:users:" + useId;

        String key2 = "follow:users:" + id;

        //求交集，共同关注
        Set<String> values = stringRedisTemplate.opsForSet().intersect(key1, key2);

        if (values==null||values.isEmpty()){
            return Result.ok();
        }

        List<Long> ids = values.stream().map(Long::valueOf).collect(Collectors.toList());

        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());


        return Result.ok(users);
    }
}
