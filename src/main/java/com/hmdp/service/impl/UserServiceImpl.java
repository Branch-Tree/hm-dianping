package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 发送短信验证码并保存验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //检验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }

        //获取验证码
        String code = RandomUtil.randomNumbers(6);

        //保存到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_USER_KEY+phone,code,2, TimeUnit.MINUTES);
        log.debug("发送验证码成功，验证码：{}",code);

        return Result.ok();
    }

    /**
     * 实现登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //检验手机号
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //格式错误
            return Result.fail("手机格式错误");
        }

        //判断验证码是否正确
        //从redis中获取信息
        Object cacheCode=stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_USER_KEY+phone);
        String code= loginForm.getCode();
        //不正确报错
        if(cacheCode==null||!cacheCode.toString().equals(code)){
            return Result.fail("验证码错误");
        }

        User user=query().eq("phone",phone).one();
        //新用户，添加到数据库
        if(user==null){
            user=createUserWithPhone(phone);
        }


        //获取token
        String token= UUID.randomUUID().toString();
        //将User对象转为Hash对象
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fileName,fileValue)->fileValue.toString()));

        //保存用户信息到redis中
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,userMap);

        //设置token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    //
    private User createUserWithPhone(String phone) {
        User user=new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+RandomUtil.randomString((10)));
        save(user);
        return user;

    }
}
