package com.genzopia.Instagame.reelview;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * Device Performance Detector - Detects device capabilities for adaptive optimization
 * Classifies devices as LOW_END, MID_RANGE, or HIGH_END for performance tuning
 * 
 * PRODUCTION-READY: Comprehensive device detection for smooth video playback
 */
public class DevicePerformanceDetector {
    private static final String TAG = "DevicePerformanceDetector";
    
    public enum PerformanceLevel {
        LOW_END,     // Budget devices with limited RAM/CPU
        MID_RANGE,   // Standard devices with moderate performance
        HIGH_END     // Flagship devices with high performance
    }
    
    private static PerformanceLevel cachedPerformanceLevel = null;
    
    /**
     * Detect device performance level using multiple metrics
     * CACHED: Result is cached for performance (device specs don't change)
     */
    public static PerformanceLevel detectPerformanceLevel(Context context) {
        if (cachedPerformanceLevel != null) {
            return cachedPerformanceLevel;
        }
        
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            
            // Get total RAM in GB
            long totalRamMB = memoryInfo.totalMem / (1024 * 1024);
            double totalRamGB = totalRamMB / 1024.0;
            
            // Get CPU cores
            int cpuCores = Runtime.getRuntime().availableProcessors();
            
            // Get Android API level (newer = better performance features)
            int apiLevel = Build.VERSION.SDK_INT;
            
            // Get device year (approximate based on API level)
            int deviceYear = getApproximateDeviceYear(apiLevel);
            
            // Performance scoring algorithm
            int performanceScore = calculatePerformanceScore(totalRamGB, cpuCores, apiLevel, deviceYear);
            
            // Classify device based on score
            PerformanceLevel level;
            if (performanceScore >= 80) {
                level = PerformanceLevel.HIGH_END;
            } else if (performanceScore >= 50) {
                level = PerformanceLevel.MID_RANGE;
            } else {
                level = PerformanceLevel.LOW_END;
            }
            
            cachedPerformanceLevel = level;
            
            Log.i(TAG, String.format("Device Performance Analysis: RAM=%.1fGB, Cores=%d, API=%d, Year=%d, Score=%d, Level=%s",
                  totalRamGB, cpuCores, apiLevel, deviceYear, performanceScore, level.name()));
            
            return level;
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting performance level, defaulting to MID_RANGE", e);
            cachedPerformanceLevel = PerformanceLevel.MID_RANGE;
            return cachedPerformanceLevel;
        }
    }
    
    /**
     * Calculate performance score based on device metrics
     * Score range: 0-100 (higher = better performance)
     */
    private static int calculatePerformanceScore(double ramGB, int cpuCores, int apiLevel, int deviceYear) {
        int score = 0;
        
        // RAM scoring (40% weight)
        if (ramGB >= 8.0) {
            score += 40;  // 8GB+ = flagship
        } else if (ramGB >= 6.0) {
            score += 35;  // 6GB = high-end
        } else if (ramGB >= 4.0) {
            score += 25;  // 4GB = mid-range
        } else if (ramGB >= 3.0) {
            score += 15;  // 3GB = low-mid
        } else if (ramGB >= 2.0) {
            score += 8;   // 2GB = low-end
        } else {
            score += 2;   // <2GB = very low-end
        }
        
        // CPU cores scoring (25% weight)
        if (cpuCores >= 8) {
            score += 25;  // 8+ cores = flagship
        } else if (cpuCores >= 6) {
            score += 20;  // 6 cores = high-end
        } else if (cpuCores >= 4) {
            score += 15;  // 4 cores = mid-range
        } else if (cpuCores >= 2) {
            score += 8;   // 2 cores = low-end
        } else {
            score += 2;   // 1 core = very low-end
        }
        
        // Android API level scoring (20% weight)
        if (apiLevel >= 33) {      // Android 13+
            score += 20;
        } else if (apiLevel >= 30) { // Android 11+
            score += 18;
        } else if (apiLevel >= 28) { // Android 9+
            score += 15;
        } else if (apiLevel >= 26) { // Android 8+
            score += 12;
        } else if (apiLevel >= 23) { // Android 6+
            score += 8;
        } else {
            score += 3;   // Very old Android
        }
        
        // Device age scoring (15% weight)
        int currentYear = 2026;
        int deviceAge = currentYear - deviceYear;
        if (deviceAge <= 1) {
            score += 15;  // Brand new
        } else if (deviceAge <= 2) {
            score += 12;  // 1-2 years old
        } else if (deviceAge <= 3) {
            score += 9;   // 2-3 years old
        } else if (deviceAge <= 4) {
            score += 6;   // 3-4 years old
        } else if (deviceAge <= 5) {
            score += 3;   // 4-5 years old
        } else {
            score += 1;   // Very old device
        }
        
        return Math.min(100, score); // Cap at 100
    }
    
    /**
     * Estimate device year based on Android API level
     */
    private static int getApproximateDeviceYear(int apiLevel) {
        // Approximate mapping of API levels to years
        if (apiLevel >= 34) return 2024;      // Android 14
        if (apiLevel >= 33) return 2023;      // Android 13
        if (apiLevel >= 31) return 2022;      // Android 12
        if (apiLevel >= 30) return 2021;      // Android 11
        if (apiLevel >= 29) return 2020;      // Android 10
        if (apiLevel >= 28) return 2019;      // Android 9
        if (apiLevel >= 26) return 2018;      // Android 8
        if (apiLevel >= 24) return 2017;      // Android 7
        if (apiLevel >= 23) return 2016;      // Android 6
        if (apiLevel >= 21) return 2015;      // Android 5
        return 2014; // Older devices
    }
    
    /**
     * Check if device is low-end (needs aggressive optimization)
     */
    public static boolean isLowEndDevice(Context context) {
        return detectPerformanceLevel(context) == PerformanceLevel.LOW_END;
    }
    
    /**
     * Check if device is high-end (can handle full optimization)
     */
    public static boolean isHighEndDevice(Context context) {
        return detectPerformanceLevel(context) == PerformanceLevel.HIGH_END;
    }
    
    /**
     * Get recommended video quality based on device performance
     */
    public static String getRecommendedVideoQuality(Context context) {
        PerformanceLevel level = detectPerformanceLevel(context);
        switch (level) {
            case HIGH_END:
                return "1080p";
            case MID_RANGE:
                return "720p";
            case LOW_END:
            default:
                return "480p";
        }
    }
    
    /**
     * Get recommended frame rate based on device performance
     */
    public static int getRecommendedFrameRate(Context context) {
        PerformanceLevel level = detectPerformanceLevel(context);
        switch (level) {
            case HIGH_END:
                return 60; // 60fps for flagship devices
            case MID_RANGE:
                return 30; // 30fps for mid-range devices
            case LOW_END:
            default:
                return 24; // 24fps for low-end devices
        }
    }
}