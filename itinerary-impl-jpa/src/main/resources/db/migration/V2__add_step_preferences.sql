-- Add preferences column to itinerary_steps table
ALTER TABLE itinerary_steps
ADD COLUMN preferences JSONB;

-- Add index for preference queries (optional but recommended)
CREATE INDEX idx_itinerary_steps_preferences ON itinerary_steps USING gin(preferences);

-- Comment for documentation
COMMENT ON COLUMN itinerary_steps.preferences IS
'Step-level preferences (JSONB). If present, overrides trip-level preferences for this step.';

