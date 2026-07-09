-- V5のCHECK制約は仕様確定前のドラフト値(8種)で作成されていたため、
-- 確定仕様の4種(WORK/PRIVATE/LEARNING/OTHER)に絞り込む。
-- V5は適用済みのため編集せず、新しいマイグレーションで訂正する。
UPDATE posts SET category = NULL WHERE category NOT IN ('WORK', 'PRIVATE', 'LEARNING', 'OTHER');

ALTER TABLE posts DROP CONSTRAINT chk_posts_category;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_category CHECK (category IN ('WORK', 'PRIVATE', 'LEARNING', 'OTHER'));
