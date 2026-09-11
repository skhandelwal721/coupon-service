package com.northwind.coupon.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CardMaskTest {

    private final CardMask cardMask = new CardMask();

    @Test
    void keepsOnlyTheLastFourDigits() {
        assertEquals("****1111", cardMask.mask("4111111111111111"));
        assertEquals("****4444", cardMask.mask("5500000000004444"));
    }

    @Test
    void masksEverythingWhenThereIsNotEnoughToKeep() {
        assertEquals("****", cardMask.mask(null));
        assertEquals("****", cardMask.mask(""));
        assertEquals("****", cardMask.mask("41"));
    }

    /** The property we are after: the full PAN is not recoverable from the masked value. */
    @Test
    void doesNotLeaveTheFullPanInTheOutput() {
        String pan = "4111111111111111";

        assertFalse(cardMask.mask(pan).contains(pan));
        assertEquals(8, cardMask.mask(pan).length());
    }
}
