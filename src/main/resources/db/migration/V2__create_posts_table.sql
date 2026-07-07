CREATE TABLE posts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    body VARCHAR(100) NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_posts_user_id_posted_at ON posts (user_id, posted_at);
