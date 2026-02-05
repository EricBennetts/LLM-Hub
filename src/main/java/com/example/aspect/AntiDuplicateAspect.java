package com.example.aspect;

import com.example.annotation.AntiDuplicate;
import com.example.utils.UserContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class AntiDuplicateAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Around("@annotation(antiDuplicate)") // 拦截加了 @AntiDuplicate 注解的方法
    public Object around(ProceedingJoinPoint joinPoint, AntiDuplicate antiDuplicate) throws Throwable {
        // 1. 获取当前用户ID (你确认过，这里一定有值)
        Long userId = UserContext.getUserId();

        // 2. 生成一个唯一的 Redis Key
        // 格式例如: anti_dup:create:101
        String methodName = joinPoint.getSignature().getName();
        String key = "anti_dup:" + methodName + ":" + userId;

        // 3. 尝试获取锁 (Set If Not Exists)
        // 参数: key, value, 过期时间, 时间单位
        // 如果 Key 不存在，设置成功返回 true；如果 Key 已存在，返回 false
        Boolean isSuccess = redisTemplate.opsForValue().setIfAbsent(key, "1", antiDuplicate.time(), TimeUnit.SECONDS);

        // 4. 如果设置失败，说明锁还在（还在冷却时间内）
        if (isSuccess == null || !isSuccess) {
            // 直接抛出异常，阻止方法继续执行
            throw new RuntimeException("操作太快了，请休息 " + antiDuplicate.time() + " 秒后再试！");
        }

        // 5. 如果设置成功，放行，执行原来的 Controller 方法
        return joinPoint.proceed();
    }
}