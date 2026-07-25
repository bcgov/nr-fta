package ca.bc.gov.nrs.fta.configuration;

import ca.bc.gov.nrs.fta.dto.CodeDescriptionDto;
import ca.bc.gov.nrs.fta.dto.CodeNameDto;
import ca.bc.gov.nrs.fta.exception.NotFoundGenericException;
import ca.bc.gov.nrs.fta.exception.RequestException;
import ca.bc.gov.nrs.fta.exception.RetriableException;
import ca.bc.gov.nrs.fta.exception.TooManyRequestsException;
import ca.bc.gov.nrs.fta.exception.UnretriableException;
import ca.bc.gov.nrs.fta.exception.UserNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
@RegisterReflectionForBinding({
  CodeDescriptionDto.class,
  CodeNameDto.class,
  NotFoundGenericException.class,
  RequestException.class,
  RetriableException.class,
  TooManyRequestsException.class,
  UnretriableException.class,
  UserNotFoundException.class
})
public class GlobalConfiguration {

  @Bean
  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder.build();
  }
}
