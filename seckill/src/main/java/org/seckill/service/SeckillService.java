package org.seckill.service;

import org.apache.ibatis.annotations.Param;
import org.seckill.dto.Exposer;
import org.seckill.dto.SeckillExecution;
import org.seckill.entity.Seckill;
import org.seckill.exception.RepeatKillException;
import org.seckill.exception.SeckillCloseException;
import org.seckill.exception.SeckillException;

import java.util.List;

/**
 * 业务接口：站在‘使用者’的角度设计接口
 * 如何站在使用者角度设计的三个方面：
 * 方法定义粒度，参数（越简练，越直接），返回类型（return类型/异常）
 */
public interface SeckillService {

    /**
     * 查询全部的秒杀记录
     * @return
     */
    List<Seckill> getSeckillList();

    /**
     * 查询单个秒杀记录
     * @param seckillId
     * @return
     */
    Seckill getById(long seckillId);


    //---------往下，是我们最重要的行为接口--------//

    /**
     * 在秒杀开启时输出秒杀接口地址，否则输出系统时间和秒杀时间
     * @param seckillId
     * @return
     */
    Exposer exportSeckillUrl(long seckillId);

    /**
     * 执行秒杀操作，有可能失败，有可能成功，所以要抛出我们允许的异常
     * @param seckillId
     * @param userPhone
     * @param md5
     * @return
     * @throws SeckillException
     * @throws RepeatKillException
     * @throws SeckillCloseException
     */
    SeckillExecution executeSeckill(@Param("seckillId")long seckillId, @Param("userPhone")long userPhone, @Param("md5")String md5)
            throws SeckillException, RepeatKillException, SeckillCloseException;

    /**
     *执行秒杀操作by，存储过程
     * @return
     * @throws SeckillException
     * @throws RepeatKillException
     * @throws SeckillCloseException
     */
    SeckillExecution executeSeckillProcedure(@Param("seckillId")long seckillId, @Param("userPhone")long userPhone, @Param("md5")String md5)
            throws SeckillException, RepeatKillException, SeckillCloseException;
}
