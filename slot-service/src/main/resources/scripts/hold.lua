-- Atomically take one place from a slot counter.
-- KEYS[1] = slot:remaining:{slotId}
-- Returns 1 when a place was taken, 0 when the counter is missing or empty.
local remaining = redis.call('GET', KEYS[1])
if remaining and tonumber(remaining) > 0 then
    redis.call('DECR', KEYS[1])
    return 1
end
return 0
