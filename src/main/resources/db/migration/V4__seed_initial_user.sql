-- 開発用の初期ユーザー。フェーズ1にはユーザー登録UIがないため、ここで1件だけ投入する。
-- パスワードハッシュは BCryptPasswordEncoder で生成済みの値(平文パスワードは別途開発者間で共有する)。
INSERT INTO users (email, password_hash, created_at)
VALUES (
    'demo@tsumory.local',
    '$2b$12$6QVzxQsS6R.sjZRowinoJOiMBFRsjtM3ZGDwTh2iSEJdIt2x3YLEe',
    CURRENT_TIMESTAMP
);
