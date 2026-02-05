// ===== 文件: com/example/exception/GlobalExceptionHandler.java =====
package com.example.exception;

import com.example.pojo.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 捕获所有 RuntimeException
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        e.printStackTrace(); // 打印错误日志到控制台
        // 将异常信息封装成 Result 返回给前端
        // 这里的 e.getMessage() 就是你在切面里写的 "操作太快了..."
        return Result.error(e.getMessage());
    }
}