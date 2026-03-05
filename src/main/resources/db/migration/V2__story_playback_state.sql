CREATE TABLE IF NOT EXISTS story_playback_state (
    robot_id UUID PRIMARY KEY REFERENCES robot(id) ON DELETE CASCADE,
    story_id UUID NOT NULL REFERENCES story(id) ON DELETE CASCADE,
    current_segment_order INT NOT NULL,
    updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_story_playback_updated_at ON story_playback_state(updated_at);
