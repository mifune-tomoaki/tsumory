ALTER TABLE posts
    ADD COLUMN category VARCHAR(20),
    ADD CONSTRAINT chk_posts_category CHECK (
        category IN (
            'WORK',
            'RELATIONSHIP',
            'HEALTH',
            'HOBBY',
            'LEARNING',
            'EVENT',
            'EMOTION',
            'OTHER'
        )
    );
