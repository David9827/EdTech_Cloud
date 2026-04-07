ALTER TABLE message
    ADD COLUMN IF NOT EXISTS robot_id UUID;

UPDATE message m
SET robot_id = cs.robot_id
FROM conversation_session cs
WHERE m.session_id = cs.id
  AND m.robot_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_message_robot'
    ) THEN
        ALTER TABLE message
            ADD CONSTRAINT fk_message_robot
            FOREIGN KEY (robot_id) REFERENCES robot(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM message WHERE robot_id IS NULL) THEN
        ALTER TABLE message
            ALTER COLUMN robot_id SET NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_message_robot_created_id
    ON message(robot_id, created_at DESC, id DESC);
