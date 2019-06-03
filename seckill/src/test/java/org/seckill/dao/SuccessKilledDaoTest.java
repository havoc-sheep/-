package org.seckill.dao;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.seckill.entity.SuccessKilled;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
//告诉junit spring的配置文件
@ContextConfiguration({"classpath:spring/spring-dao.xml"})
public class SuccessKilledDaoTest {

    @Resource
    private SuccessKilledDao successKilledDao;

    @Test
    public void insertSuccessKilled() throws Exception{
        /**
         * 第一次：insertCount=1
         * 第二次：insertCount=0
         */
        long id =1000;
        long phone = 13502181128L;
        int insertCount = successKilledDao.insertSuccessKilled(id, phone);
        System.out.println("insertCount="+ insertCount);
    }

    @Test
    public void queryByIdWithSeckill() throws Exception{

        long id =1000;
        long phone = 13502181128L;
        SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(id, phone);
        System.out.println(successKilled);
        System.out.println(successKilled.getSeckill());
    }
}