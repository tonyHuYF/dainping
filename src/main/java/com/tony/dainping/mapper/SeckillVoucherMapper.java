package com.tony.dainping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tony.dainping.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

}
