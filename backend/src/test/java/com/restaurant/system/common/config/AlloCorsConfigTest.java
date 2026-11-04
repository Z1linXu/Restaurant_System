package com.restaurant.system.common.config;
import com.restaurant.system.template.bootstrap.TemplateTestCorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.cors.CorsConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
class AlloCorsConfigTest {
 static class Registry extends CorsRegistry { CorsConfiguration api() { return getCorsConfigurations().get("/api/**"); } }
 @Test void alloUsesOnlyExplicitTestOrigins() {
  try(var c=new AnnotationConfigApplicationContext()) {
   c.getEnvironment().setActiveProfiles("allo-test"); c.register(RestCorsConfig.class,TemplateTestCorsConfig.class); c.refresh();
   assertThat(c.getBeansOfType(RestCorsConfig.class)).isEmpty();
   var r=new Registry(); c.getBean(TemplateTestCorsConfig.class).addCorsMappings(r);
   assertThat(r.api().checkOrigin("https://test.lanzhounoodlesmtl.com")).isEqualTo("https://test.lanzhounoodlesmtl.com");
   assertThat(r.api().checkOrigin("https://attacker.invalid")).isNull();
   assertThat(r.api().checkOrigin("https://test.lanzhounoodlesmtl.com:444")).isNull();
  }
 }
 @Test void productionKeepsOriginalConfiguration() {
  try(var c=new AnnotationConfigApplicationContext()) {
   c.getEnvironment().setActiveProfiles("prod"); c.register(RestCorsConfig.class,TemplateTestCorsConfig.class); c.refresh();
   assertThat(c.getBeansOfType(TemplateTestCorsConfig.class)).isEmpty(); assertThat(c.getBeansOfType(RestCorsConfig.class)).hasSize(1);
  }
 }
}
