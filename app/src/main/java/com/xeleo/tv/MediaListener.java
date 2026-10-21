package com.xeleo.tv;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing with notifications. Android only lets an app read the active media sessions (what Spotify or
 * YouTube is playing) when it holds notification access, and this service is what that access is granted to.
 */
public class MediaListener extends NotificationListenerService {
}
