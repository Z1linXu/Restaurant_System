package com.restaurant.system.modules;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class StoreModuleAccessEvaluatorSpringInstantiationTest {

    @Test
    void springCanInstantiateEvaluatorWithRuntimeConstructor() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(StoreModuleRepository.class, () -> mock(StoreModuleRepository.class));
        context.registerBean(StoreModuleCapabilityProvider.class, () -> mock(StoreModuleCapabilityProvider.class));
        context.register(StoreModuleAccessEvaluator.class);

        assertDoesNotThrow(context::refresh);
        assertNotNull(context.getBean(StoreModuleAccessEvaluator.class));

        context.close();
    }
}
