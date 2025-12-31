package com.nimbly.phshoesbackend.useraccount.web;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;

class PhShoesUserAccountServiceApplicationTest {

    @Test
    void main_callsSpringApplicationRun() {
        // Arrange
        String[] args = {"--spring.main.web-application-type=none"};

        try (MockedStatic<SpringApplication> springApplication = Mockito.mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(PhShoesUserAccountServiceApplication.class, args))
                    .thenReturn(mock(ConfigurableApplicationContext.class));

            // Act
            PhShoesUserAccountServiceApplication.main(args);

            // Assert
            springApplication.verify(() -> SpringApplication.run(PhShoesUserAccountServiceApplication.class, args));
        }
    }
}
