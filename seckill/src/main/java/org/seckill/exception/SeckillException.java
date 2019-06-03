package org.seckill.exception;

/**
 * Created by yhj on 2019/5/15
 * 秒杀相关的所有业务异常
 */
public class SeckillException extends RuntimeException {

    public SeckillException(String message) {
        super(message);
    }

    public SeckillException(String message, Throwable cause) {
        super(message, cause);
    }
}
