package ar.cocos.challenge.web.dto.response;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Data
@Builder
public class Error {

    @Builder.Default
    private HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
    @Builder.Default
    private String message = "An error occurred processing the request";

}
