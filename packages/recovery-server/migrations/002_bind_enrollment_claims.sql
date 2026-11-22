ALTER TABLE aura_enrollment_challenges
  ADD COLUMN IF NOT EXISTS requested_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS platform TEXT NOT NULL DEFAULT 'android',
  ADD COLUMN IF NOT EXISTS label TEXT NOT NULL DEFAULT '';

