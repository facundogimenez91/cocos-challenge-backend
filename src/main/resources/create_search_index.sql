-- Execute this SQL to improve search performance with trigram indexes

-- Enable trigram extension (needed for % operator and similarity)
CREATE
EXTENSION IF NOT EXISTS pg_trgm;

-- Create GIN index for ticker to speed up partial and fuzzy matches
CREATE INDEX IF NOT EXISTS idx_instruments_ticker_trgm
    ON instruments USING GIN (ticker gin_trgm_ops);

-- Create GIN index for name to speed up partial and fuzzy matches
CREATE INDEX IF NOT EXISTS idx_instruments_name_trgm
    ON instruments USING GIN (name gin_trgm_ops);