package com.tony.dainping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tony.dainping.dto.LoginFormDTO;
import com.tony.dainping.dto.Result;
import com.tony.dainping.dto.UserDTO;
import com.tony.dainping.entity.User;
import com.tony.dainping.mapper.UserMapper;
import com.tony.dainping.service.IUserService;
import com.tony.dainping.utils.RedisConstants;
import com.tony.dainping.utils.RegexUtils;
import com.tony.dainping.utils.SystemConstants;
import com.tony.dainping.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //判断手机号是否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存到redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //模拟发送验证码，这里只记录
        log.info("发送验证码短信成功，验证码：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();

        //判断手机号正否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //判断验证码是否一致
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误！");
        }

        //查询手机号是否存在用户
        User user = query().eq("phone", phone).one();

        if (user == null) {
            //创建新用户
            user = createUserByPhone(phone);
        }

        //保存用户到redis中，以随机码token作为Key
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));


        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;

        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //设置token过期时间
//        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime time = LocalDateTime.now();

        String dateStr = time.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        //1个月的第几天
        int dayOfMonth = time.getDayOfMonth();

        String key = "sign:" + userId + dateStr;

        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime time = LocalDateTime.now();

        String dateStr = time.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        //1个月的第几天
        int dayOfMonth = time.getDayOfMonth();
//        int dayOfMonth = 8;

        String key = "sign:" + userId + dateStr;

        //获取bitmap数据 bitfied get key u 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        Long num = result.get(0);

        if (num == null || num == 0) {
            return Result.ok(0);
        }

        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                // bitmap数据最后一位与1做与运算 = 0 ，即为最后一位是0 ，未签到
                break;
            } else {
                //已签到
                count++;
            }
            //右移一位
            num >>>= 1;
        }


        return Result.ok(count);
    }
}
