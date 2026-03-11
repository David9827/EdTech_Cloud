CREATE TYPE reminder_status AS ENUM (
    'ACTIVE',
    'CANCELLED',
    'DONE'
);

CREATE TABLE reminder (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    child_id UUID REFERENCES child(id) ON DELETE SET NULL,
    robot_id UUID REFERENCES robot(id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT,
    schedule_at TIMESTAMP NOT NULL,
    status reminder_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    cancelled_at TIMESTAMP,
    done_at TIMESTAMP
);

CREATE INDEX idx_reminder_user ON reminder(user_id);
CREATE INDEX idx_reminder_robot_status_schedule ON reminder(robot_id, status, schedule_at);
CREATE INDEX idx_reminder_status_schedule ON reminder(status, schedule_at);
