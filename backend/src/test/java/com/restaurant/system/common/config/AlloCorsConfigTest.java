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
   assertThat(r.api().checkOrigin("https://pos.allonouilles.com")).isEqualTo("https://pos.allonouilles.com");
   assertThat(r.api().checkOrigin("https://restaurant-pad.local")).isEqualTo("https://restaurant-pad.local");
   assertThat(r.api().checkOrigin("http://restaurant-pad.local")).isNull();
   assertThat(r.api().checkOrigin("https://restaurant-pad.local:444")).isNull();
   assertThat(r.api().checkOrigin("https://restaurant-pad.local.attacker.invalid")).isNull();
   assertThat(r.api().checkOrigin("http://pos.allonouilles.com")).isNull();
   assertThat(r.api().checkOrigin("http://127.0.0.1:28182")).isNull();
   assertThat(r.api().checkOrigin("https://attacker.invalid")).isNull();
   assertThat(r.api().checkOrigin("https://test.lanzhounoodlesmtl.com:444")).isNull();
  }
 }
 @Test void bundledOriginIsNotAddedToOtherTemplateProfile() {
  try(var c=new AnnotationConfigApplicationContext()) {
   c.getEnvironment().setActiveProfiles("template-test");c.register(TemplateTestCorsConfig.class);c.refresh();
   var r=new Registry();c.getBean(TemplateTestCorsConfig.class).addCorsMappings(r);
   assertThat(r.api().checkOrigin("https://restaurant-pad.local")).isNull();
  }
 }
 @Test void productionKeepsOriginalConfiguration() {
  try(var c=new AnnotationConfigApplicationContext()) {
   c.getEnvironment().setActiveProfiles("prod"); c.register(RestCorsConfig.class,TemplateTestCorsConfig.class); c.refresh();
   assertThat(c.getBeansOfType(TemplateTestCorsConfig.class)).isEmpty(); assertThat(c.getBeansOfType(RestCorsConfig.class)).hasSize(1);
  }
 }
 @Test void isolatedQaOriginRequiresExplicitQaProfile() {
  try(var c=new AnnotationConfigApplicationContext()) {
   c.getEnvironment().setActiveProfiles("allo-test","allo-qa");c.register(TemplateTestCorsConfig.class);c.refresh();
   var r=new Registry();c.getBean(TemplateTestCorsConfig.class).addCorsMappings(r);
   assertThat(r.api().checkOrigin("http://127.0.0.1:28182")).isEqualTo("http://127.0.0.1:28182");
   assertThat(r.api().checkOrigin("https://attacker.invalid")).isNull();
  }
 }
}
