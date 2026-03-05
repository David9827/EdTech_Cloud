-- Demo seed data for Ed-Tech
-- Safe to run multiple times (uses ON CONFLICT).

BEGIN;

-- app_user
INSERT INTO app_user (
    id, email, password_hash, full_name, role, is_active, created_at, updated_at
)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'demo.parent@edtech.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOqI.4Q9YqL0hW8sELpAo0P5NdQ4KJIp6',
    'Demo Parent',
    'PARENT',
    true,
    now(),
    now()
)
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    updated_at = now();

-- child
INSERT INTO child (
    id, user_id, name, birth_date, created_at
)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Bé Bông',
    '2020-05-10',
    now()
)
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    name = EXCLUDED.name,
    birth_date = EXCLUDED.birth_date;

-- robot (uses the same robot_id you are testing with)
INSERT INTO robot (
    id, name, device_key, status, volume, created_at
)
VALUES (
    '4f864132-2c3f-4fff-9811-19a840e93473',
    'Robot Bup Be',
    'demo-device-key-4f864132',
    'IDLE',
    65,
    now()
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    volume = EXCLUDED.volume;

-- map user and robot
INSERT INTO user_robot (user_id, robot_id)
VALUES ('11111111-1111-1111-1111-111111111111', '4f864132-2c3f-4fff-9811-19a840e93473')
ON CONFLICT (user_id, robot_id) DO NOTHING;

-- topic
INSERT INTO topic (
    id, name, description, min_age, max_age, created_at
)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    'Kham pha thien nhien',
    'Chu de ve dong vat, cay co va moi truong xung quanh',
    4,
    8,
    now()
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    min_age = EXCLUDED.min_age,
    max_age = EXCLUDED.max_age;

-- story
INSERT INTO story (
    id, title, status, topic_id, min_age, max_age, created_at, img_url
)
VALUES (
    '44444444-4444-4444-4444-444444444444',
    'Cuoc phieu luu cua Tho Trang',
    'PUBLISHED',
    '33333333-3333-3333-3333-333333333333',
    4,
    8,
    now(),
    'https://example.com/story-thumb-rabbit.jpg'
)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    status = EXCLUDED.status,
    topic_id = EXCLUDED.topic_id,
    min_age = EXCLUDED.min_age,
    max_age = EXCLUDED.max_age,
    img_url = EXCLUDED.img_url;

-- story segments
INSERT INTO story_segment (id, story_id, segment_order, content, emotion)
VALUES
(
    '55555555-5555-5555-5555-555555555551',
    '44444444-4444-4444-4444-444444444444',
    1,
    'Sang som, Tho Trang thuc day va nghe tieng chim hot trong rung.',
    'HAPPY'
),
(
    '55555555-5555-5555-5555-555555555552',
    '44444444-4444-4444-4444-444444444444',
    2,
    'Tho Trang gap Ban Rua con va ca hai cung tim duong den ho nuoc xanh.',
    'EXCITED'
),
(
    '55555555-5555-5555-5555-555555555553',
    '44444444-4444-4444-4444-444444444444',
    3,
    'Cuoi cung, ca hai ve nha an toan va hen mai se cung kham pha tiep.',
    'CALM'
)
ON CONFLICT (id) DO UPDATE SET
    story_id = EXCLUDED.story_id,
    segment_order = EXCLUDED.segment_order,
    content = EXCLUDED.content,
    emotion = EXCLUDED.emotion;

-- conversation session
INSERT INTO conversation_session (
    id, robot_id, child_id, topic_id, started_at, ended_at
)
VALUES (
    '66666666-6666-6666-6666-666666666666',
    '4f864132-2c3f-4fff-9811-19a840e93473',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    now() - interval '5 minutes',
    now()
)
ON CONFLICT (id) DO UPDATE SET
    robot_id = EXCLUDED.robot_id,
    child_id = EXCLUDED.child_id,
    topic_id = EXCLUDED.topic_id,
    ended_at = EXCLUDED.ended_at;

-- messages in demo conversation
INSERT INTO message (id, session_id, role, content, emotion, created_at)
VALUES
(
    '77777777-7777-7777-7777-777777777771',
    '66666666-6666-6666-6666-666666666666',
    'USER',
    'Chao robot, ke cho con nghe mot cau chuyen ve tho nhe',
    'HAPPY',
    now() - interval '4 minutes'
),
(
    '77777777-7777-7777-7777-777777777772',
    '66666666-6666-6666-6666-666666666666',
    'ASSISTANT',
    'Duoc roi, co mot chu tho trang rat de thuong dang doi con trong rung xanh.',
    'HAPPY',
    now() - interval '3 minutes 50 seconds'
),
(
    '77777777-7777-7777-7777-777777777773',
    '66666666-6666-6666-6666-666666666666',
    'USER',
    'Tho co ban nao di cung khong a',
    'NEUTRAL',
    now() - interval '3 minutes 40 seconds'
),
(
    '77777777-7777-7777-7777-777777777774',
    '66666666-6666-6666-6666-666666666666',
    'ASSISTANT',
    'Co, Ban Rua con di cung va hai ban ay giup do nhau rat vui ve.',
    'EXCITED',
    now() - interval '3 minutes 30 seconds'
)
ON CONFLICT (id) DO UPDATE SET
    session_id = EXCLUDED.session_id,
    role = EXCLUDED.role,
    content = EXCLUDED.content,
    emotion = EXCLUDED.emotion,
    created_at = EXCLUDED.created_at;

COMMIT;

-- Quick check:
-- SELECT id, name FROM robot WHERE id = '4f864132-2c3f-4fff-9811-19a840e93473';
-- SELECT id, title, status FROM story WHERE id = '44444444-4444-4444-4444-444444444444';
-- SELECT session_id, role, content FROM message WHERE session_id = '66666666-6666-6666-6666-666666666666' ORDER BY created_at;
