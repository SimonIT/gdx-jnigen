package com.badlogic.gdx.jnigen.gc;

import com.badlogic.gdx.jnigen.Global;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public class BaseTest {

    @BeforeAll
    public static void setUp() {
        Global.init();
    }

    @BeforeEach
    @AfterEach
    public void emptyGC() {
        while (GCHandler.nativeObjectCount() != 0)
            System.gc();
    }
}