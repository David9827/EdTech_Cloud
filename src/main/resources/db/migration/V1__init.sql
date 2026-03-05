CREATE TYPE user_role AS ENUM (
    'PARENT',
    'ADMIN'
);

CREATE TYPE robot_status AS ENUM (
    'IDLE',
    'LISTENING',
    'THINKING',
    'SPEAKING',
    'STORY_PLAYING',
    'ERROR'
);

CREATE TYPE message_role AS ENUM (
    'USER',
    'ASSISTANT',
    'SYSTEM'
);

CREATE TYPE emotion_type AS ENUM (
    'NEUTRAL',
    'HAPPY',
    'SAD',
    'ANGRY',
    'SURPRISED',
    'EXCITED',
    'CALM'
);

CREATE TYPE story_status AS ENUM (
    'DRAFT',
    'PUBLISHED',
    'ARCHIVED'
);

CREATE TYPE content_type AS ENUM (
    'TEXT',
    'IMAGE',
    'AUDIO'
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150),
    role user_role DEFAULT 'PARENT',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE child (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(100),
    birth_date DATE,
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    token VARCHAR(500) UNIQUE NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    is_revoked BOOLEAN DEFAULT false
);

CREATE TABLE robot (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    device_key VARCHAR(255) UNIQUE NOT NULL,
    status robot_status DEFAULT 'IDLE',
    volume INT DEFAULT 50 CHECK (volume BETWEEN 0 AND 100),
    created_at TIMESTAMP DEFAULT now()
);
--- user_robot (mapping)
CREATE TABLE user_robot (
    user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    robot_id UUID REFERENCES robot(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, robot_id)
);

--- conversation
CREATE TABLE topic (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    min_age INT,
    max_age INT,
    created_at TIMESTAMP DEFAULT now()
);

---conversation_session
CREATE TABLE conversation_session (
    id UUID PRIMARY KEY,
    robot_id UUID NOT NULL REFERENCES robot(id),
    child_id UUID REFERENCES child(id),
    topic_id UUID REFERENCES topic(id),
    started_at TIMESTAMP DEFAULT now(),
    ended_at TIMESTAMP
);

---message
CREATE TABLE message (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES conversation_session(id) ON DELETE CASCADE,
    role message_role NOT NULL,
    content TEXT NOT NULL,
    emotion emotion_type,
    created_at TIMESTAMP DEFAULT now()
);
---STORY
CREATE TABLE story (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    status story_status DEFAULT 'DRAFT',
    topic_id UUID REFERENCES topic(id),
    min_age INT,
    max_age INT,
    created_at TIMESTAMP DEFAULT now()
);
---story segment
CREATE TABLE story_segment (
    id UUID PRIMARY KEY,
    story_id UUID REFERENCES story(id) ON DELETE CASCADE,
    segment_order INT NOT NULL,
    content TEXT NOT NULL,
    emotion emotion_type,
    UNIQUE(story_id, segment_order)
);

CREATE INDEX idx_refresh_user ON refresh_token(user_id);
CREATE INDEX idx_message_session ON message(session_id);
CREATE INDEX idx_message_created ON message(created_at);
CREATE INDEX idx_story_topic ON story(topic_id);
CREATE INDEX idx_story_status ON story(status);
CREATE INDEX idx_session_robot ON conversation_session(robot_id);
CREATE INDEX idx_session_child ON conversation_session(child_id);
CREATE INDEX idx_topic_age ON topic(min_age, max_age);

