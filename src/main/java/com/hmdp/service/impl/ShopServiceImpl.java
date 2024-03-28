package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 商户查询缓存
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id){
        String key= RedisConstants.CACHE_SHOP_KEY +id;
        //从reids中获取商铺缓存信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        if(shopJson!=null){
            return null;
        }

        String lockKey="lock:shop:"+id;
        Shop shop=null;
        try {
            //获取互斥锁
            boolean isLock = tryLock(lockKey);
            //判断
            if (!isLock) {

                //没有获取互斥锁成功
                Thread.sleep(50);
                return queryWithMutex(id);
            }


            shop = getById(id);
            Thread.sleep(200);
            //数据库不存在，返回错误
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            //存在，写入redis

            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unLock(lockKey);
        }
        //返回
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


    /**
     * 更新店铺数据
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
