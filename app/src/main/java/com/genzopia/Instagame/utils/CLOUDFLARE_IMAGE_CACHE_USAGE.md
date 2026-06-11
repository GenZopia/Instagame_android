# Cloudflare Image Cache Usage Guide

## Overview
The `CloudflareImageCache` utility handles downloading and caching profile images from the Cloudflare Worker API with proper authentication headers.

## How It Works
1. **First Load**: Fetches image from Cloudflare API with `x-api-key` header
2. **Downloads & Caches**: Saves image to local cache and stores path in SharedPreferences
3. **Subsequent Loads**: Uses cached local file (no API call needed)
4. **Cache Clearing**: Automatically clears cache on logout

## Implementation in ProfileFragment

### Before (Not Working - Missing API Key Header)
```java
Glide.with(this)
    .load(profilePhotoUrl)  // ❌ Direct URL load fails without x-api-key header
    .into(profileImage);
```

### After (Working - With Caching)
```java
// Extract remote key from URL
String remoteKey = null;
if (profilePhotoUrl != null && profilePhotoUrl.contains("?key=")) {
    remoteKey = profilePhotoUrl.substring(profilePhotoUrl.indexOf("?key=") + 5);
}

// Check cache first
String cachedImagePath = CloudflareImageCache.INSTANCE.getCachedImagePath(context);
if (cachedImagePath != null) {
    // Load from cache (instant)
    Glide.with(this).load(new File(cachedImagePath)).into(profileImage);
} else if (remoteKey != null) {
    // Fetch from API with proper headers and cache it
    CloudflareImageCache.INSTANCE.fetchProfileImage(
        context,
        userId,
        remoteKey,
        new CloudflareImageCache.ImageCacheCallback() {
            @Override
            public void onSuccess(String localFilePath) {
                Glide.with(this).load(new File(localFilePath)).into(profileImage);
            }
            
            @Override
            public void onFailure(String message) {
                profileImage.setImageResource(R.drawable.profile);
            }
        }
    );
}
```

## When to Clear Cache

### 1. On Logout (Already Implemented)
```java
CloudflareImageCache.INSTANCE.clearCache(context);
```

### 2. When User Updates Profile Picture
```java
// After successfully uploading new profile picture
CloudflareImageCache.INSTANCE.clearCache(context);
// Then fetch the new image
```

### 3. Manual Refresh
If you need to force refresh the profile image:
```java
CloudflareImageCache.INSTANCE.clearCache(context);
// Re-fetch will happen automatically on next ProfileFragment load
```

## Benefits

✅ **Proper Authentication**: Uses `x-api-key` header for Cloudflare API  
✅ **Performance**: Only downloads once, then uses cached file  
✅ **Offline Support**: Works even when API is unreachable after first load  
✅ **Automatic Cleanup**: Cache cleared on logout  
✅ **Error Handling**: Graceful fallback to placeholder image  

## API Request Details

### Download Request (Made by CloudflareImageCache)
```bash
GET https://file-upload-worker.genzopia.workers.dev/?key=instagame/userId/filename.jpg
Headers:
  x-api-key: Genzopia@9999
```

## Debugging

Enable logs to see cache behavior:
```
adb logcat -s CloudflareImageCache
```

Log messages:
- "Using cached profile image" - Loading from cache
- "Fetching profile image from Cloudflare API" - Downloading from API
- "Profile image cached successfully" - Successfully saved to cache
- "Cache cleared" - Cache was cleared
