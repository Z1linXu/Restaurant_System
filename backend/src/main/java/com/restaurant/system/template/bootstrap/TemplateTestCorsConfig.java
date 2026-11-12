package com.restaurant.system.template.bootstrap;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
@Profile({"template-test","allo-test"})
public class TemplateTestCorsConfig implements WebMvcConfigurer {
 public void addCorsMappings(CorsRegistry registry){
  registry.addMapping("/api/**")
   .allowedOrigins("http://127.0.0.1:28080","https://test.lanzhounoodlesmtl.com","https://pos.allonouilles.com","http://127.0.0.1:28180","http://localhost:28180")
   .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
   .allowedHeaders("Content-Type","Authorization","Accept","X-Print-Device-Id","X-Print-Timestamp","X-Print-Signature")
   .allowCredentials(false).maxAge(3600);
 }
}
