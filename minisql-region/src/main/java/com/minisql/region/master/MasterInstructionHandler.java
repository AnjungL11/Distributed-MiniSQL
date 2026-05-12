package com.minisql.region.master;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public class MasterInstructionHandler {
    private static final Logger logger = LoggerFactory.getLogger(MasterInstructionHandler.class);

    private final AtomicReference<String> lastInstruction = new AtomicReference<>();

    public boolean handle(String instruction) {
        if (instruction == null || instruction.trim().isEmpty() || "NONE".equalsIgnoreCase(instruction.trim())) {
            return true;
        }
        String normalized = instruction.trim();
        lastInstruction.set(normalized);
        logger.info("Received Master instruction: {}", normalized);
        logger.warn("Unsupported Master instruction ignored for now: {}", normalized);
        return false;
    }

    public String getLastInstruction() {
        return lastInstruction.get();
    }
}
