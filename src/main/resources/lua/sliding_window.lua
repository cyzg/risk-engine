-- Atomic sliding-window counter on a ZSET.
-- KEYS[1] = window key
-- ARGV[1] = now (ms), ARGV[2] = window size (ms), ARGV[3] = unique member (eventId)
-- Returns the number of events inside the window, including this one.
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
redis.call('ZADD', key, now, ARGV[3])
redis.call('PEXPIRE', key, window)
return redis.call('ZCARD', key)
