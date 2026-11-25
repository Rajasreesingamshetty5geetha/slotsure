-- Give one place back to a slot counter, but only if the counter exists.
-- A missing counter is rebuilt from MySQL on next use, so INCR on it would be wrong.
-- KEYS[1] = slot:remaining:{slotId}
-- Returns the new value, or -1 when the counter is missing.
if redis.call('EXISTS', KEYS[1]) == 1 then
    return redis.call('INCR', KEYS[1])
end
return -1
