package org.seckill.dao.cache;

import com.dyuproject.protostuff.LinkedBuffer;
import com.dyuproject.protostuff.ProtobufIOUtil;
import com.dyuproject.protostuff.runtime.RuntimeSchema;
import org.seckill.entity.Seckill;
import org.seckill.utils.JedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;
import java.util.function.Function;

/**
 * Created by yhj on 2019/5/16
 */
public class RedisDao {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final JedisPool jedisPool;

    public RedisDao(String ip, int port){
        jedisPool = new JedisPool(ip, port);
    }


    private RuntimeSchema<Seckill> schema = RuntimeSchema.createFrom(Seckill.class);

    /**
     * 从redis获取消息
     * @param seckillId
     * @return
     */
    public Seckill getSeckill(long seckillId){
        //redis操作逻辑
        try{
            Jedis jedis = jedisPool.getResource();
            try{
                //String key = "seckill:" + seckillId;
                String key = getSeckillRedisKey(seckillId);
                //并没有实现内部序列化操作
                //get -> byte[] -> 反序列化 -> Object(Seckill)
                // 采用自定义序列化
                // protostuff: pojo.
                byte[] bytes = jedis.get(key.getBytes());
                //缓存重获取到
                if(bytes != null){
                    //空对象
                    Seckill seckill = schema.newMessage();
                    ProtobufIOUtil.mergeFrom(bytes, seckill, schema);
                    //schema被反序列化

                    return seckill;
                }
            }finally{
                jedis.close();
            }
        }catch(Exception e){
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public String putSeckill(Seckill seckill){
        //set Object(Seckill) -> 序列化 -> byte[]
        try{
            Jedis jedis = jedisPool.getResource();
            try{
                //String key = "seckill:" + seckill.getSeckillId();
                String key = getSeckillRedisKey(seckill.getSeckillId());
                byte[] bytes=ProtobufIOUtil.toByteArray(seckill, schema,
                        LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));
                //超时缓存
                int timeout = 60 * 60; //1小时
                String result = jedis.setex(key.getBytes(), timeout, bytes);
                return result;
            }finally{
                jedis.close();
            }
        }catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }


    //接下来的老师课上并没有讲

    /**
     * 根据id获取redis的key
     * @param seckillId
     * @return
     */
    private String getSeckillRedisKey(long seckillId) {
        return "seckill:" + seckillId;
    }


    /**
     * 从缓存获取，如果没有，则从数据库获取
     * 会用到分布式锁
     * @param seckillId     id
     * @param getDataFromDb 从数据库获取的方法
     * @return 返回商品信息
     */
    public Seckill getOrPutSeckill(long seckillId, Function<Long, Seckill> getDataFromDb){
        Seckill seckill = getSeckill(seckillId);
        if(seckill != null){
            return seckill;
        }

        String lockKey = "seckill:locks:getSeckill:" + seckillId;
        String lockRequestId = UUID.randomUUID().toString();
        Jedis jedis = jedisPool.getResource();
        // 尝试获取锁。
        // 锁过期时间是防止程序突然崩溃来不及解锁，而造成其他线程不能获取锁的问题。过期时间是业务容忍最长时间
        boolean getLock = JedisUtils.tryGetDistributedLock(jedis, lockKey, lockRequestId, 1000);
        if(getLock){
            // 获取到锁，从数据库拿数据, 然后存redis
            try{
                seckill = getDataFromDb.apply(seckillId);
                putSeckill(seckill);
            }catch(Exception e){

            }finally{
                // 无论如何，最后要去解锁
                JedisUtils.releaseDistributedLock(jedis, lockKey, lockRequestId);
            }
            return seckill;
        }

        // 获取不到锁，睡一下，等会再出发。sleep的时间需要斟酌，主要看业务处理速度
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return getOrPutSeckill(seckillId, getDataFromDb);
    }




}
