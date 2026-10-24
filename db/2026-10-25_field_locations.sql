-- Where each extracted field was found on the original page, from the
-- pipeline's field locator: {field: {page, box: [x0, y0, x1, y1], matched, method}},
-- with box normalised to 0-1 of the page, origin top left.
ALTER TABLE extraction_outputs ADD COLUMN IF NOT EXISTS field_locations jsonb;
