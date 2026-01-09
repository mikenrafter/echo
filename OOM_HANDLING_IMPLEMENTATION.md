# OOM (Out of Memory) Error Handling Implementation

## Overview

This document describes the implementation of graceful OOM error handling in Echo with automatic memory size reduction on subsequent boot attempts.

## Problem Statement

When users set a memory allocation that exceeds the device's available memory, an OutOfMemoryError occurs and the app crashes. Without proper handling, the same allocation attempt would occur on next boot, causing the same crash loop.

## Solution Architecture

The implementation uses a two-flag system:
1. **Persistent Memory Size** (`AUDIO_MEMORY_SIZE_KEY`): Stores the target memory allocation in bytes
2. **Verification Flag** (`MEMORY_SIZE_VERIFIED_KEY`): Boolean flag indicating whether the current memory size has been successfully allocated

### Boot Flow

```
App Boots Up
    ↓
[Check verification flag]
    ↓
Flag NOT Set? → Reduce memory by 10MB, save new size
    ↓
Attempt allocation
    ↓
Success? → Set verification flag, continue recording
    ↓
Failed? → Unset flag (triggers reduction on next boot), stop service
```

## Implementation Details

### 1. Configuration Constants (SaidIt.java)

Added two new constants:
```java
// OOM handling configuration
static final String MEMORY_SIZE_VERIFIED_KEY = "memory_size_verified";
static final long MEMORY_REDUCTION_STEP_MB = 10; // Reduce by 10MB on OOM
```

**Purpose:**
- `MEMORY_SIZE_VERIFIED_KEY`: Tracks whether the current memory setting is verified safe
- `MEMORY_REDUCTION_STEP_MB`: Amount to reduce memory on retry (10MB per boot cycle)

### 2. AudioMemory Changes (AudioMemory.java)

Modified the `allocate()` method to return a boolean instead of void:

```java
/**
 * Attempts to allocate the requested memory size.
 * @param sizeToEnsure Target memory size in bytes
 * @return true if allocation succeeded, false if OutOfMemoryError occurred
 */
synchronized public boolean allocate(long sizeToEnsure)
```

**Key Features:**
- Wraps allocation in try-catch to handle `OutOfMemoryError`
- Returns `true` on successful allocation
- Returns `false` if OOM occurs
- Clears all buffers and calls `System.gc()` on failure for cleanup
- Logs allocation success/failure with memory size in MB

### 3. Service Boot Logic (SaidItService.java)

#### Modified `innerStartListening()` Method

The boot process now:

1. **Reads preferences** to get stored memory size and verification flag
2. **Checks verification flag**:
   - If `false`: Calls `reduceMemorySizeForRetry()` to reduce by 10MB
   - If `true`: Uses stored memory size directly
3. **Attempts allocation** with the determined memory size
4. **On success**: Sets the verification flag and continues recording
5. **On failure**: Clears the flag (triggers reduction next boot) and stops

#### New Helper Method: `reduceMemorySizeForRetry()`

```java
private long reduceMemorySizeForRetry(long currentSize) {
    long reductionBytes = MEMORY_REDUCTION_STEP_MB * 1024 * 1024;
    long reducedSize = currentSize - reductionBytes;
    long minMemoryBytes = 10 * 1024 * 1024; // 10 MB minimum
    
    if (reducedSize < minMemoryBytes) {
        return minMemoryBytes;
    }
    return reducedSize;
}
```

**Features:**
- Reduces memory by exactly 10MB per boot attempt
- Enforces 10MB minimum (won't reduce below)
- Logs reduction details for debugging
- Ensures gradual walk-back on each boot

#### Updated `setMemorySize()` Method

When users manually change memory size:
1. Saves the new size
2. Resets verification flag to `false`
3. Attempts immediate allocation
4. Sets flag to `true` only if successful
5. If immediate allocation fails, flag remains `false` for retry next boot

### 4. Memory Allocation Verification

The allocation verification process:
- **Successful allocation**: Flag is set, app records normally
- **Failed allocation**: Flag is unset, app stops and waits for next boot
- **Next boot after failure**: Automatically tries with 10MB less, repeating until success

## User Experience Flow

### Scenario 1: User Sets Memory That's Too High

1. User opens settings, enters 500MB memory size
2. App attempts allocation
3. Allocation fails due to OOM
4. App shows error, service stops
5. User sees "Recording disabled" 

### Scenario 2: User Relaunches App After OOM

1. User reopens app (or app auto-restarts)
2. System checks verification flag (it's `false`)
3. Memory is reduced by 10MB (500→490MB)
4. Allocation attempted with 490MB
5. If still fails, flag remains `false` for next boot
6. If succeeds, flag is set, app records at 490MB

### Scenario 3: Gradual Walk-Back to Stable Size

```
Boot 1: User sets 500MB → OOM → Flag unset
Boot 2: System tries 490MB → OOM → Flag unset
Boot 3: System tries 480MB → OOM → Flag unset
Boot 4: System tries 470MB → Success → Flag set, recording works
```

## Advantages of This Approach

1. **No Crash Loop**: Verification flag prevents repeated allocation failures
2. **Automatic Recovery**: System finds stable memory size without user intervention
3. **User Control**: Manual memory changes are respected, re-verified immediately
4. **Gradual Reduction**: 10MB steps allow finding reasonable memory size efficiently
5. **Persistent**: Settings survive app restart and device reboot
6. **Transparent**: Users see reduced memory after recovery, can adjust if desired
7. **Minimal Code**: Leverages existing SharedPreferences infrastructure

## Configuration

To adjust the reduction step size, modify in `SaidIt.java`:

```java
static final long MEMORY_REDUCTION_STEP_MB = 10;  // Change this value
```

Examples:
- `5`: Reduces by 5MB per boot (slower walk-back, more reboots)
- `10`: Reduces by 10MB per boot (recommended)
- `20`: Reduces by 20MB per boot (faster walk-back, larger jumps)

## Logging

Key log messages for debugging:

```
D/SaidItService: Memory allocation verified: 470 MB
E/SaidItService: OutOfMemoryError during allocation of 500 MB
E/SaidItService: Memory allocation failed for 490 MB. Will retry with reduced size on next boot.
D/SaidItService: Reducing memory size from 500 MB to 490 MB for retry.
```

## Testing Recommendations

1. **Test OOM Recovery**:
   - Set memory very high (near device maximum)
   - Launch app and verify gradual reduction
   - Check logs for memory sizes being tried

2. **Test Manual Override**:
   - Set memory, let it verify
   - Change memory to different value
   - Verify new value is tested on next allocation

3. **Test Minimum Boundary**:
   - Set memory that results in walking back to 10MB
   - Verify 10MB minimum is enforced
   - Confirm app works at 10MB

4. **Test Disk Mode**:
   - Test OOM handling with both Memory Only and Batch to Disk modes
   - Ensure disk buffer initialization occurs after successful allocation

## Files Modified

1. **SaidIt.java**
   - Added `MEMORY_SIZE_VERIFIED_KEY` constant
   - Added `MEMORY_REDUCTION_STEP_MB` constant

2. **AudioMemory.java**
   - Modified `allocate()` to return `boolean`
   - Added OOM exception handling
   - Added logging for allocation success/failure

3. **SaidItService.java**
   - Modified `innerStartListening()` for verification flow
   - Added `reduceMemorySizeForRetry()` helper method
   - Updated `setMemorySize()` for verification handling

## Future Enhancements

1. **User Notification**: Show toast when memory is auto-reduced
2. **Adaptive Stepping**: Use larger steps initially, smaller steps near minimum
3. **Memory Suggestions**: Offer recommended sizes based on device
4. **Analytics**: Track how often OOM occurs and what final sizes are used
5. **Persistent History**: Store multiple previous attempts to avoid re-trying failed sizes
