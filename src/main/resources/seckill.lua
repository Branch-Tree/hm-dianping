


--优惠券id
local voucherId=ARGV[1]

--用户id
local userId=ARGV[2]

--库存
local stockKey='seckill:stock:'..voucherId

--订单id
local orderKey='seckill:order:'..voucherId

--判断库存是否足够
if(tonumber(redis.call('get',stockKey))<=0) then
    --库存不足返回1
    return 1
end

--判断用户是否下单
if(redis.call('sismember',orderKey,userId)==1) then
    --存在，用户重复下单
    return 2
end

--扣库存
redis.call('incrby',stockKey,-1)

--下单
redis.call('sadd',orderKey,userId)

return 0
