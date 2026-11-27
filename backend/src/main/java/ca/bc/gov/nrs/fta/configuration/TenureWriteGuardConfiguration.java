package ca.bc.gov.nrs.fta.configuration;

import ca.bc.gov.nrs.fta.tenure.controller.RangeFileWriteGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers {@link RangeFileWriteGuard} on the tenure endpoints; it picks the writes itself. */
@Configuration
public class TenureWriteGuardConfiguration implements WebMvcConfigurer {

  private final RangeFileWriteGuard rangeFileWriteGuard;

  public TenureWriteGuardConfiguration(RangeFileWriteGuard rangeFileWriteGuard) {
    this.rangeFileWriteGuard = rangeFileWriteGuard;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(rangeFileWriteGuard).addPathPatterns("/api/fta/tenures/*/**");
  }
}
