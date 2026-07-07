CREATE TABLE diaries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    diary_on DATE NOT NULL,
    body TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_diaries_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_diaries_user_id_diary_on UNIQUE (user_id, diary_on)
);
