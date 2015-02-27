package io.magnetic.vamp_core.rest_api.notification

import io.magnetic.vamp_common.notification.{DefaultPackageMessageResolverProvider, LoggingNotificationProvider}

trait RestApiNotificationProvider extends LoggingNotificationProvider with DefaultPackageMessageResolverProvider