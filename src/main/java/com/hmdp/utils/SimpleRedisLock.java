package com.hmdp.utils;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private static final String KEY_PREFIX="lock:";
    private static final String Id_PREFIX= UUID.randomUUID().toString()+"-";
    StringRedisTemplate stringRedisTemplate;


    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate){
        this.name=name;
        this.stringRedisTemplate=stringRedisTemplate;
    }
    @Override
    public boolean tyrLock(long timeoutSec) {
        String threadId =Id_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {

        String threadId=Id_PREFIX+Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(threadId);
        if(threadId.equals(id)){
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }


    }
}
