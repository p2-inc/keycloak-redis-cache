/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.phasetwo.keycloak.common;

import java.time.Duration;
import java.time.Instant;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;

@JBossLog
public class ExpirationUtils {

  /**
   * Checks whether the {@code entity} is expired
   *
   * @param entity to check
   * @param allowInfiniteValues sets how null values are interpreted, if true entity with expiration
   *     equal to {@code null} is interpreted as never expiring entity, if false entities with
   *     {@code null} expiration are interpreted as expired entities
   * @return true if the {@code entity} is expired (expiration time is in the past or now), false
   *     otherwise
   */
  public static boolean isExpired(ExpirableEntity entity, boolean allowInfiniteValues) {
    Long expiration = entity.getExpiration();
    if (!allowInfiniteValues && expiration == null) return false;
    long now = Time.currentTimeMillis();
    boolean expired = expiration != null && expiration <= now;
    log.tracef(
        "isExpired %d <= %d ? %b %s",
        expiration, now, expired, expiration != null ? expiration - now : 0);
    return expired;
  }

  public static boolean isNotExpired(Object entity) {
    return !isExpired((ExpirableEntity) entity, true);
  }

  public static String fromNow(ExpirableEntity entity) {
    if (entity == null || entity.getExpiration() == null) return "never";
    else return fromNow(entity.getExpiration());
  }

  public static String fromNow(long timestampMillis) {
    Instant now = Instant.now();
    Instant then = Instant.ofEpochMilli(timestampMillis);
    Duration duration = Duration.between(now, then);

    long millis = duration.toMillis();
    boolean future = millis > 0;
    long absMillis = Math.abs(millis);

    long days = absMillis / (1000 * 60 * 60 * 24);
    long hours = (absMillis / (1000 * 60 * 60)) % 24;
    long minutes = (absMillis / (1000 * 60)) % 60;
    long seconds = (absMillis / 1000) % 60;

    StringBuilder timePart = new StringBuilder();
    if (days > 0) timePart.append(days).append(" day").append(days > 1 ? "s" : "");
    else if (hours > 0) timePart.append(hours).append(" hour").append(hours > 1 ? "s" : "");
    else if (minutes > 0) timePart.append(minutes).append(" minute").append(minutes > 1 ? "s" : "");
    else if (seconds > 0) timePart.append(seconds).append(" second").append(seconds > 1 ? "s" : "");
    else timePart.append("just now");

    if (!timePart.toString().equals("just now")) {
      timePart.insert(0, future ? "in " : "").append(future ? "" : " ago");
    }

    timePart.append(" (").append(millis).append(" ms)");

    return timePart.toString();
  }
}
