package net.lenni0451.noteblockbot.data;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bucket;

import java.time.Duration;

public class RateLimiter {

    private static final LoadingCache<Long, Bucket> userBuckets = Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).build(key -> Bucket.builder()
            .addLimit(limit -> limit.capacity(Config.RateLimits.userMaxRequestsPerMinute).refillIntervally(Config.RateLimits.userMaxRequestsPerMinute, Duration.ofMinutes(1)))
            .build());
    private static final LoadingCache<Long, Bucket> guildBuckets = Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).build(key -> Bucket.builder()
            .addLimit(limit -> limit.capacity(Config.RateLimits.guildMaxRequestsPerMinute).refillIntervally(Config.RateLimits.guildMaxRequestsPerMinute, Duration.ofMinutes(1)))
            .build());

    public static boolean tryUser(final long userId) {
        return userBuckets.get(userId).tryConsume(1);
    }

    public static boolean tryGuild(final long guildId) {
        return guildBuckets.get(guildId).tryConsume(1);
    }

}
