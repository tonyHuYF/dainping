package com.tony.dainping.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tony.dainping.dto.Result;
import com.tony.dainping.entity.SeckillVoucher;
import com.tony.dainping.entity.VoucherOrder;
import com.tony.dainping.mapper.VoucherOrderMapper;
import com.tony.dainping.service.ISeckillVoucherService;
import com.tony.dainping.service.IVoucherOrderService;
import com.tony.dainping.utils.RedisIdWorker;
import com.tony.dainping.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //查询是未在秒杀期间、库存是否充足
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始！");
        }

        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }

        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        //减扣库存
        boolean success = seckillVoucherService.update().setSql("stock = stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            //扣减不足
            return Result.fail("库存不足");
        }

        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setId(orderId);

        save(voucherOrder);

        //返回订单id
        return Result.ok(orderId);
    }
}
