-- Swiss-system tournaments: everyone plays a set number of rounds against opponents on a similar score,
-- each round paired when the previous one is finished. swiss_rounds is set when the tournament starts.
ALTER TABLE tournaments DROP CONSTRAINT tournaments_format_check;
ALTER TABLE tournaments ADD CONSTRAINT tournaments_format_check
    CHECK (format IN ('ROUND_ROBIN', 'SINGLE_ELIMINATION', 'SWISS'));
ALTER TABLE tournaments ADD COLUMN swiss_rounds INTEGER CHECK (swiss_rounds BETWEEN 1 AND 31);
