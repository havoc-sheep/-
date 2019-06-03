package org.seckill.service.impl;

import org.apache.commons.collections.MapUtils;
import org.seckill.dao.SeckillDao;
import org.seckill.dao.SuccessKilledDao;
import org.seckill.dao.cache.RedisDao;
import org.seckill.dto.Exposer;
import org.seckill.dto.SeckillExecution;
import org.seckill.entity.Seckill;
import org.seckill.entity.SuccessKilled;
import org.seckill.enums.SeckillStatEnum;
import org.seckill.exception.RepeatKillException;
import org.seckill.exception.SeckillCloseException;
import org.seckill.exception.SeckillException;
import org.seckill.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by yhj on 2019/5/15
 */
///@Component @Service @Dao @Controller
@Service
public class SeckillServiceImpl implements SeckillService {
    //日志对象
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    //加入一个混淆字符串（秒杀接口）的salt，为了避免用户猜出我们的md5值，值任意给，越复杂越好（可以启用其他加密方法吗）
    private final String salt = "alihwjdpdd'1.*";

    //注入Service依赖
    @Autowired  //@Resource，不需要自己new一个实现类
    private SeckillDao seckillDao;

    @Autowired //@Resource
    private SuccessKilledDao successKilledDao;

    @Autowired
    private RedisDao redisDao;

    @Override
    public List<Seckill> getSeckillList() {
        return seckillDao.queryAll(0,4);
    }

    @Override
    public Seckill getById(long seckillId) {

        return seckillDao.queryById(seckillId);//更改
        //return redisDao.getOrPutSeckill(seckillId, id->seckillDao.queryById(id));
    }

    @Override
    public Exposer exportSeckillUrl(long seckillId) {
        //优化点:缓存优化:超时的基础上维护一致性
        //1.访问redis
        Seckill seckill = redisDao.getSeckill(seckillId);
        if(seckill == null){
            //2.访问数据库
            seckill = seckillDao.queryById(seckillId);
            if(seckill == null){
                //查不到秒杀产品的记录
                return new Exposer(false, seckillId);
            }else{
                //3.放入redis
                redisDao.putSeckill(seckill);
            }
        }

//        Seckill seckill = seckillDao.queryById(seckillId);
//        if(seckill == null){
//            //查不到秒杀产品的记录
//            return new Exposer(false, seckillId);
//        }


        //若是秒杀未开启
        Date startTime = seckill.getStartTime();
        Date endTime = seckill.getEndTime();

        //系统当前时间
        Date nowTime = new Date();
        if(startTime.getTime() > nowTime.getTime() || endTime.getTime() < nowTime.getTime()){
            return new Exposer(false, seckillId,nowTime.getTime(), startTime.getTime(),endTime.getTime());
        }

        //秒杀开启，返回秒杀商品的id，用给接口加密的md5
        //转化特定字符串的过程，不可逆
        String md5 = getMD5(seckillId);
        return new Exposer(true, md5, seckillId);
    }

    //不希望被别人访问，下面的业务也用到md5
    private String getMD5(long seckillId){
        String base = seckillId + "/" + salt;
        String md5 = DigestUtils.md5DigestAsHex(base.getBytes());
        //System.out.println(md5);
        return md5;
    }

    //秒杀是否成功，成功：减库存+增加购买明细；失败：抛出异常，事务回滚
    /**
     * 使用注解控制事务方法的优点:
     * 1.开发团队达成一致约定，明确标注事务方法的编程风格
     * 2.保证事务方法的执行时间尽可能短，不要穿插其他网络操作RPC/HTTP请求或者剥离到事务方法外部
     * 3.不是所有的方法都需要事务，如只有一条修改操作、只读操作不要事务控制
     */
    @Override
    @Transactional
    public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5)
            throws SeckillException, RepeatKillException, SeckillCloseException {

        if(md5==null || !md5.equals(getMD5(seckillId))){
            throw new SeckillException("seckill data rewrite"); //秒杀数据被重写了
        }
        //执行秒杀逻辑：减库存+增加购买明细
        Date nowTime = new Date();

        //更改之后的结果
        try{
            //记录购买行为
            int insertCount = successKilledDao.insertSuccessKilled(seckillId,userPhone);
            //唯一：seckillId，userPhone
            if(insertCount <= 0){
                //重复秒杀
                throw new RepeatKillException("seckill repeated");
            }else{
                //减库存，热点商品竞争
                int updateCount = seckillDao.reduceNumber(seckillId, nowTime);
                if(updateCount<=0){
                    //没有更新库存记录，说明秒杀结束
                    throw new SeckillCloseException("seckill is closed");
                }else{
                    //秒杀成功，得到成功插入的明细记录，并返回成功秒杀的信息
                    SuccessKilled successKilled=successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                    return new SeckillExecution(seckillId, SeckillStatEnum.SUCCESS, successKilled);
                }
            }
            //下面两个catch先写是为了能够抛出我们允许的异常
        }catch(SeckillCloseException e1){
            throw e1;
        }catch(RepeatKillException e2){
            throw e2;
        }catch(Exception e){
            logger.error(e.getMessage(),e);
            //所有编译期异常转化为运行期异常
            throw new SeckillException("seckill inner error: " + e.getMessage());
        }



//        try{
//            //减库存
//            int updateCount = seckillDao.reduceNumber(seckillId, nowTime);
//            if(updateCount<=0){
//                //没有更新库存记录，说明秒杀结束
//                throw new SeckillCloseException("seckill is closed");
//            }else{
//                //否则更新了库存，秒杀成功，增加明细，记录购买行为
//                int insertCount = successKilledDao.insertSuccessKilled(seckillId,userPhone);
//                //唯一：seckillId，userPhone
//                if(insertCount <= 0){
//                    //重复秒杀
//                    throw new RepeatKillException("seckill repeated");
//                }else{
//                    //秒杀成功，得到成功插入的明细记录，并返回成功秒杀的信息
//                    SuccessKilled successKilled=successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
//                    return new SeckillExecution(seckillId, SeckillStatEnum.SUCCESS, successKilled);
//                }
//            }
//            //下面两个catch先写是为了能够抛出我们允许的异常
//        }catch(SeckillCloseException e1){
//            throw e1;
//        }catch(RepeatKillException e2){
//            throw e2;
//        }catch(Exception e){
//            logger.error(e.getMessage(),e);
//            //所有编译期异常转化为运行期异常
//            throw new SeckillException("seckill inner error: " + e.getMessage());
//        }


    }

    @Override
    public SeckillExecution executeSeckillProcedure(long seckillId, long userPhone, String md5)  {
        if(md5 == null || !md5.equals(getMD5(seckillId))){
            return new SeckillExecution(seckillId, SeckillStatEnum.DATE_REWRITE);
        }

        Date killTime = new Date();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("seckillId", seckillId);
        map.put("phone", userPhone);
        map.put("killTime", killTime);
        map.put("result", null);
        //执行储存过程,result被复制
        try {
            seckillDao.killByProcedure(map);
            //获取result
            int result = MapUtils.getInteger(map, "result", -2);
            if (result == 1) {
                SuccessKilled sk = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                return new SeckillExecution(seckillId, SeckillStatEnum.SUCCESS, sk);
            } else {
                return new SeckillExecution(seckillId, SeckillStatEnum.stateOf(result));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
            return new SeckillExecution(seckillId, SeckillStatEnum.INNER_ERROR);
        }

    }
}
