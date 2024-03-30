package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

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
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 商户查询缓存
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
       //  解决缓存穿透
//        Shop shop = cacheClient
//                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //  逻辑过期解决缓存击穿
         Shop shop = cacheClient
                 .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 7.返回
        return Result.ok(shop);
    }


    /**
     * 创建线程池
     */
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    /**
//     * 逻辑过期解决缓存击穿
//     * @param id
//     * @return
//     */
//    public Shop queryWithLogicalExpire(Long id){
//        String key= CACHE_SHOP_KEY+id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if(StrUtil.isBlank(shopJson)){
//            //未命中，直接返回
//            return null;
//        }
//        //获取redis中的信息
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        //没过期，直接返回
//        if(expireTime.isAfter(LocalDateTime.now())){
//            return shop;
//        }
//        //尝试获取互斥锁
//        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
//        boolean isLock=tryLock(lockKey);
//        if(isLock){
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    //重建缓存
//                    this.saveShop2Redis(id,20L);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }finally {
//                    unLock(lockKey);
//                }
//            });
//        }
//        return shop;
//    }
//
//    /**
//     * 互斥锁的方式解决缓存击穿
//     * @param id
//     * @return
//     */
//    public Shop queryWithMutex(Long id){
//        String key= CACHE_SHOP_KEY +id;
//        //从reids中获取商铺缓存信息
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//
//        if(shopJson!=null){
//            return null;
//        }
//
//        String lockKey="lock:shop:"+id;
//        Shop shop=null;
//        try {
//            //获取互斥锁
//            boolean isLock = tryLock(lockKey);
//            //判断
//            if (!isLock) {
//
//                //没有获取互斥锁成功
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//
//
//            shop = getById(id);
//            //数据库不存在，返回错误
//            if (shop == null) {
//                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//
//            //存在，写入redis
//
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        }catch (Exception e){
//            throw new RuntimeException(e);
//        }finally {
//            //释放互斥锁
//            unLock(lockKey);
//        }
//        //返回
//        return shop;
//    }
//
//    private boolean tryLock(String key){
//        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }


//    public void saveShop2Redis(Long id, Long expireSeconds){
//        Shop shop=getById(id);
//
//        RedisData redisData=new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//    }

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
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
