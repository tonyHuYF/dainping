package com.tony.dainping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tony.dainping.dto.Result;
import com.tony.dainping.entity.Shop;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {


    Result queryShopById(Long id);

    Result update(Shop shop);
}
