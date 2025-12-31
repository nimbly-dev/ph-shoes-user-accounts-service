package com.nimbly.phshoesbackend.useraccount.core.subscribe.impl;

import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscribeServiceImplTest {

    @Mock
    private UnsubscribeTokenCodec unsubscribeTokenCodec;
    @Mock
    private SuppressionService suppressionService;

    private SubscribeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubscribeServiceImpl(unsubscribeTokenCodec, suppressionService);
    }

    @Test
    void resubscribe_throwsWhenTokenBlank() {
        // Arrange
        String token = " ";

        // Act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.resubscribe(token));

        // Assert
        assertNotNull(exception);
    }

    @Test
    void resubscribe_unsuppressesWhenValid() {
        // Arrange
        when(unsubscribeTokenCodec.decodeAndVerify("token")).thenReturn("hash1");

        // Act
        service.resubscribe("token");

        // Assert
        verify(suppressionService).unsuppressHash("hash1");
    }
}
