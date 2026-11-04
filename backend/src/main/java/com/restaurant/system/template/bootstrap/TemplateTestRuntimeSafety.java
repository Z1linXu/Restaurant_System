package com.restaurant.system.template.bootstrap;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.printing.PrintingRuntimePolicyProperties;
import java.util.*;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import com.restaurant.system.common.response.ApiResponse;

/** Test-only safety is checked before Flyway can migrate, not after application initialization. */
@Configuration
@Profile({"template-test","allo-test"})
public class TemplateTestRuntimeSafety {
 @Bean
 FlywayConfigurationCustomizer isolatedTestDatabaseBeforeMigration(Environment env,PrintingRuntimePolicyProperties policy){
  return configuration -> validateBeforeMigration(env,policy);
 }
 public static void validateBeforeMigration(Environment env,PrintingRuntimePolicyProperties policy){
  String url=env.getProperty("spring.datasource.url","");
  if(!env.getProperty("spring.flyway.url","").isBlank()||!env.getProperty("spring.flyway.user","").isBlank())
      throw new IllegalStateException("Test Flyway must share the isolated application datasource");
  Set<String> active=new HashSet<>(Arrays.asList(env.getActiveProfiles()));
  boolean allo=active.contains("allo-test");
  if(allo && active.contains("template-test"))throw new IllegalStateException("Independent Test profiles cannot be combined");
  Set<String> urls=allo
      ?Set.of("jdbc:postgresql://db:5432/restaurant_allo_nouilles_test","jdbc:postgresql://127.0.0.1:25442/restaurant_allo_nouilles_test")
      :Set.of("jdbc:postgresql://db:5432/restaurant_template_test","jdbc:postgresql://127.0.0.1:25432/restaurant_template_test");
  if(!urls.contains(url) || !(allo?"allo_test":"template_test").equals(env.getProperty("spring.datasource.username"))
     ||!"test".equals(env.getProperty("app.environment")))
      throw new IllegalStateException("Test profile requires its independently isolated Test database");
  if(!new HashSet<>(policy.getAllowedModes()).equals(Set.of("MOCK","DISABLED"))||policy.isEndpointConfigurationEnabled())
      throw new IllegalStateException("Test runtime must be endpoint-free MOCK/DISABLED only");
  if(env.getProperty("UBER_EATS_ENABLED",Boolean.class,false)||env.getProperty("UBER_EATS_WORKER_ENABLED",Boolean.class,false))
      throw new IllegalStateException("Test runtime cannot enable external platform processing");
 }
 @RestController
 @Profile({"template-test","allo-test"})
 @RequestMapping("/api/v1/stores/{storeId}/template/runtime-safety")
 public static class SafetyController {
  private final AuthorizationService auth;
  private final Environment env;
  private final PrintingRuntimePolicyProperties policy;
  public SafetyController(AuthorizationService auth,Environment env,PrintingRuntimePolicyProperties policy){this.auth=auth;this.env=env;this.policy=policy;}
  @GetMapping
  public ApiResponse<Map<String,Object>> safety(@PathVariable Long storeId){
   auth.requireManagerOrOwnerForStore(storeId,Capability.ADMIN_PRINTING_MANAGE,Capability.ADMIN_MENU_MANAGE);
   Map<String,Object> proof=new LinkedHashMap<>();
   proof.put("allowed_printing_modes",policy.getAllowedModes());
   proof.put("endpoint_configuration_enabled",policy.isEndpointConfigurationEnabled());
   proof.put("environment",env.getProperty("app.environment"));
   proof.put("profile",Arrays.asList(env.getActiveProfiles()).contains("allo-test")?"allo-test":"template-test");
   proof.put("build_sha",env.getProperty("app.build.sha","UNVERSIONED_LOCAL"));
   proof.put("evidence_ref","authenticated live Test-only policy bean; checked before Flyway");
   return ApiResponse.success(proof);
  }
 }
}
