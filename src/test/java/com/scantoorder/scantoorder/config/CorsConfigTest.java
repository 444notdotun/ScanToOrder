package com.scantoorder.scantoorder.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CorsConfigTest {

    @Test
    void testThatTheListIsTrimmedAndBlankAndTrailingSlashesAreDropped() {
        assertArrayEquals(new String[]{"https://www.platelyy.com", "https://platelyy.com", "https://old.example.app"},
                CorsConfig.originsFrom(" https://www.platelyy.com/ , https://platelyy.com,, https://old.example.app ,  ", "http://localhost:3000"));
    }

    @Test
    void testThatAnEmptyListFallsBackToTheOneFrontendAddress() {
        assertArrayEquals(new String[]{"https://www.platelyy.com"}, CorsConfig.originsFrom("", "https://www.platelyy.com/"));
        assertArrayEquals(new String[]{"http://localhost:3000"}, CorsConfig.originsFrom(null, "http://localhost:3000"));
        assertArrayEquals(new String[]{"http://localhost:3000"}, CorsConfig.originsFrom(" , ", "http://localhost:3000"));
    }
}
