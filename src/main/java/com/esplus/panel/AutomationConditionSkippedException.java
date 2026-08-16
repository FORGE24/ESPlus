package com.esplus.panel;

/**
 * Thrown when a conditional operation evaluates to false — treated as a "skip" not a failure.
 */
final class AutomationConditionSkippedException extends RuntimeException {
    AutomationConditionSkippedException(String message) {
        super(message);
    }
}
