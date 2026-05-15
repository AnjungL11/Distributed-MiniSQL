package com.minisql.region.master;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterInstructionHandlerTest {
    @Test
    void ignoresEmptyInstruction() {
        MasterInstructionHandler handler = new MasterInstructionHandler();

        assertTrue(handler.handle("NONE"));
        assertTrue(handler.handle(""));
    }

    @Test
    void recordsUnknownInstructionWithoutThrowing() {
        MasterInstructionHandler handler = new MasterInstructionHandler();

        assertFalse(handler.handle("MigrateRegion"));
        assertEquals("MigrateRegion", handler.getLastInstruction());
    }
}
