package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.utils.TimeText;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeTextTest {
    @Test
    void testThatMidnightAndNoonAreWrittenTheWayAPersonSaysThem() {
        assertEquals("12:00 am", TimeText.clock(LocalDateTime.of(2026, 11, 16, 0, 0)));
        assertEquals("12:00 pm", TimeText.clock(LocalDateTime.of(2026, 11, 16, 12, 0)));
    }

    @Test
    void testThatOtherTimesHaveNoLeadingZeroAndAnAmOrPm() {
        assertEquals("6:30 pm", TimeText.clock(LocalDateTime.of(2026, 11, 16, 18, 30)));
        assertEquals("9:05 am", TimeText.clock(LocalDateTime.of(2026, 11, 16, 9, 5)));
    }

    @Test
    void testThatADayAndTimeReadsAsOneLine() {
        assertEquals("16 Nov 2026, 12:00 am", TimeText.dayAndClock(LocalDateTime.of(2026, 11, 16, 0, 0)));
    }
}
