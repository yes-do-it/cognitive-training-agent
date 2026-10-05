package cn.xfone.ai.trigger.http;

import cn.xfone.ai.api.response.Response;
import cn.xfone.ai.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TrainingExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Response<Void> handleBusinessException(RuntimeException ex) {
        return Response.<Void>builder().code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(ex.getMessage()).data(null).build();
    }
}
