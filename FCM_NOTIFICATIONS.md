# FCM Push Notifications — Data Payload Guide

All notifications must be sent as **data-only** payloads (no `notification` block).  
This ensures they are handled by the app in both foreground and background.

---

## Topics

| Topic | Audience |
|-------|----------|
| `all_users` | Every user |
| `uid_{userId}` | A specific user |

---

## Base Payload (required for all notifications)

```json
{
  "message": {
    "topic": "all_users",
    "data": {
      "title": "Your Title Here",
      "body": "Your message here."
    }
  }
}
```

---

## Key-Value Pairs

| Key | Required | Description |
|-----|----------|-------------|
| `title` | ✅ | Notification title |
| `body` | ✅ | Notification body text |
| `imageUrl` | ❌ | Full URL of image to show (any aspect ratio) |
| `action` | ❌ | Deep link action on tap (see actions below) |
| `targetId` | ❌ | ID used by the action (gameId, videoId, etc.) |

---

## Actions (`action` key)

| Value | Navigates to | `targetId` needed? |
|-------|-------------|-------------------|
| `open_home` | Home tab | No |
| `open_game` | Home tab + opens game | Yes — `gameId` |
| `open_video` | Reels/Dashboard tab + opens video | Yes — `videoId` |
| `open_profile` | Profile tab | No |

---

## Examples

### Plain text notification → Home
```json
{
  "message": {
    "topic": "all_users",
    "data": {
      "title": "New games added!",
      "body": "Check out what's new on Instagame.",
      "action": "open_home"
    }
  }
}
```

### Image notification → open a specific game
```json
{
  "message": {
    "topic": "all_users",
    "data": {
      "title": "Play Now 🎮",
      "body": "A new game is waiting for you!",
      "imageUrl": "https://example.com/banner.jpg",
      "action": "open_game",
      "targetId": "GAME_ID_HERE"
    }
  }
}
```

### Notify a specific user → open video
```json
{
  "message": {
    "topic": "uid_USER_ID_HERE",
    "data": {
      "title": "Someone liked your video!",
      "body": "Tap to see it.",
      "action": "open_video",
      "targetId": "VIDEO_ID_HERE"
    }
  }
}
```

---

## Sending via Firebase Console

1. Go to **Firebase Console → Messaging → New Campaign**
2. Select **Notifications** → fill title & body
3. Under **Additional options → Custom data**, add keys like `action`, `targetId`, `imageUrl`
4. Under **Target**, choose **Topic** and enter e.g. `all_users`

## Sending via REST API

```bash
curl -X POST https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "topic": "all_users",
      "data": {
        "title": "Hello!",
        "body": "This is a test notification.",
        "action": "open_home"
      }
    }
  }'
```

> Get `YOUR_ACCESS_TOKEN` using a Firebase service account with `firebase-adminsdk` role.
