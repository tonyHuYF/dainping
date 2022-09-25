package com.tony.dainping.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tony.dainping.dto.Result;
import com.tony.dainping.entity.ShopType;
import com.tony.dainping.mapper.ShopTypeMapper;
import com.tony.dainping.service.IShopTypeService;
import com.tony.dainping.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryTypeList() {
        //去redis查询数据
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        List<String> cacheList = stringRedisTemplate.opsForList().range(key, 0, -1);

        //如果存在，则返回
        if (cacheList.size() > 0) {
            List<ShopType> result = new ArrayList<>();
            cacheList.forEach(p -> {
                ShopType shopType = JSONUtil.toBean(p, ShopType.class);
                result.add(shopType);
            });

            return Result.ok(result);
        }

        //不存在，去数据库查
        List<ShopType> dataList = query().orderByAsc("sort").list();

        if (ObjectUtil.isEmpty(dataList)) {
            return Result.fail("数据不存在");
        }

        List<String> cacheString = new ArrayList<>();

        dataList.forEach(p -> {
            cacheString.add(JSONUtil.toJsonStr(p));
        });

        stringRedisTemplate.opsForList().rightPushAll(key, cacheString);
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return Result.ok(dataList);
    }
}
