-- 動作確認用テストデータ投入スクリプト。
-- Flywayマイグレーションではないので db/migration には置かず、
-- 手動でpsql/pgAdminから実行する(Flywayのチェックサム管理対象外)。
--
-- 実行例:
--   docker exec -i tsumory-postgres-1 psql -U myuser -d tsumory < scripts/seed-verification-data.sql
--   (または pgAdmin のクエリツールに貼り付けて実行)
--
-- 内容:
--   V4マイグレーションで投入済みのdemoユーザー(demo@tsumory.local)に対して、
--   過去7日分(6日前〜今日)のつぶやきを投入する。
--   このうち直近3日分(おととい・昨日・今日)はあえて日記を生成しないままにしておき、
--   フェーズ2の「遡り生成」機能の動作確認に使える状態にする。
--
-- 再実行可能: 冒頭で対象期間のposts/diariesを一度削除してから投入し直す。

DO $$
DECLARE
  v_user_id BIGINT;
BEGIN
  SELECT id INTO v_user_id FROM users WHERE email = 'demo@tsumory.local';
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'demo@tsumory.local が見つかりません。V4マイグレーションが適用されているか確認してください。';
  END IF;

  DELETE FROM diaries WHERE user_id = v_user_id AND diary_on >= CURRENT_DATE - 6;
  DELETE FROM posts WHERE user_id = v_user_id AND posted_at >= ((CURRENT_DATE - 6)::timestamptz);
END $$;

-- つぶやき(過去7日分、1日2〜3件) ----------------------------------------

WITH u AS (SELECT id FROM users WHERE email = 'demo@tsumory.local')
INSERT INTO posts (user_id, body, posted_at, category)
SELECT u.id, v.body, v.posted_at, v.category
FROM u,
(VALUES
  -- 6日前
  ('朝は少し肌寒かったけれど、コーヒーを淹れてから出社したら頭がすっきりした。', ((CURRENT_DATE - 6) + TIME '08:15')::timestamptz, 'PRIVATE'),
  ('午前中の定例会議で仕様のすり合わせに時間がかかり、思ったより疲れた。', ((CURRENT_DATE - 6) + TIME '11:40')::timestamptz, 'WORK'),
  ('夕方から近所を30分だけジョギングした。汗をかいたら気持ちが軽くなった。', ((CURRENT_DATE - 6) + TIME '19:30')::timestamptz, 'OTHER'),

  -- 5日前
  ('同僚とランチでカレーを食べながら、最近のプロジェクトの相談に乗ってもらった。', ((CURRENT_DATE - 5) + TIME '12:20')::timestamptz, 'WORK'),
  ('午後はコードレビューに集中できて、思ったより早く片付いた。', ((CURRENT_DATE - 5) + TIME '15:45')::timestamptz, 'WORK'),
  ('寝る前に少しだけ技術書を読んだ。読んだ内容は明日にでもまとめておきたい。', ((CURRENT_DATE - 5) + TIME '22:10')::timestamptz, 'LEARNING'),

  -- 4日前
  ('朝から雨で、在宅勤務に切り替えた。オンライン会議が続いて画面疲れがひどい。', ((CURRENT_DATE - 4) + TIME '09:05')::timestamptz, 'WORK'),
  ('お昼はパスタを茹でて自炊。たまには自分で作ると落ち着く。', ((CURRENT_DATE - 4) + TIME '13:00')::timestamptz, 'PRIVATE'),
  ('雨音を聞きながら、ソファでのんびり過ごした。', ((CURRENT_DATE - 4) + TIME '20:40')::timestamptz, 'OTHER'),

  -- 3日前
  ('休日なので朝はゆっくり過ごし、溜まっていた洗濯物を片付けた。', ((CURRENT_DATE - 3) + TIME '10:30')::timestamptz, 'PRIVATE'),
  ('資格試験のテキストを1章分読み進めた。思ったより理解に時間がかかった。', ((CURRENT_DATE - 3) + TIME '14:15')::timestamptz, 'LEARNING'),
  ('夕方は近所のカフェで気分転換。新しいメニューがおいしかった。', ((CURRENT_DATE - 3) + TIME '18:50')::timestamptz, 'OTHER'),

  -- 2日前(日記は未生成のまま)
  ('スーパーに買い出しに行ったら、いつもの卵が売り切れていて少し困った。', ((CURRENT_DATE - 2) + TIME '09:40')::timestamptz, 'PRIVATE'),
  ('お昼休みにジムに寄って軽く筋トレ。継続できているのが地味にうれしい。', ((CURRENT_DATE - 2) + TIME '12:30')::timestamptz, 'OTHER'),
  ('納期が近いタスクが残っていて、少し焦り気味。明日中に片付けたい。', ((CURRENT_DATE - 2) + TIME '17:20')::timestamptz, 'WORK'),

  -- 昨日(日記は未生成のまま)
  ('友人から誕生日のメッセージが届いて、ちょっとした近況報告で盛り上がった。', ((CURRENT_DATE - 1) + TIME '09:15')::timestamptz, 'PRIVATE'),
  ('読んでいた小説をようやく読み終えた。最後の展開に驚かされた。', ((CURRENT_DATE - 1) + TIME '21:00')::timestamptz, 'OTHER'),
  ('来月の小旅行の計画を少し立てた。行き先を考えるだけで楽しい。', ((CURRENT_DATE - 1) + TIME '21:45')::timestamptz, 'PRIVATE'),

  -- 今日(まだ進行中の日なので投稿数は少なめ、分類もまだ付いていない想定)
  ('今朝は近所を軽く散歩してから出社した。空気が気持ちよかった。', ((CURRENT_DATE - 0) + TIME '07:50')::timestamptz, NULL),
  ('新しいタスクに取り掛かり始めた。要件を読み込むところからスタート。', ((CURRENT_DATE - 0) + TIME '09:30')::timestamptz, NULL)
) AS v(body, posted_at, category);

-- 日記(6日前〜3日前の4日分のみ。直近3日分はあえて未生成のままにする) ------

WITH u AS (SELECT id FROM users WHERE email = 'demo@tsumory.local')
INSERT INTO diaries (user_id, diary_on, body, generated_at)
SELECT u.id, v.diary_on, v.body, v.generated_at
FROM u,
(VALUES
  (
    CURRENT_DATE - 6,
    '今日は少し肌寒い朝からのスタートだったが、コーヒーを飲んで頭を切り替えてから出社した。午前中の定例会議では仕様のすり合わせに時間がかかり、思った以上に疲れを感じる一日に。それでも夕方に近所を30分ジョギングしたことで、心身ともにリフレッシュできたようだ。',
    ((CURRENT_DATE - 6) + TIME '23:50')::timestamptz
  ),
  (
    CURRENT_DATE - 5,
    '同僚とのランチでプロジェクトの相談に乗ってもらい、気持ちが軽くなった一日だった。午後はコードレビューに集中でき、想定より早く作業を終えられたのは収穫。夜には少しだけ技術書に目を通し、学びを積み重ねる時間も確保できた。',
    ((CURRENT_DATE - 5) + TIME '23:50')::timestamptz
  ),
  (
    CURRENT_DATE - 4,
    '朝からの雨で在宅勤務に切り替え、オンライン会議が続いたことで画面疲れを感じる一日だった。お昼にパスタを自炊したことが、ちょっとした気分転換になったようだ。夜は雨音を聞きながらゆっくり過ごし、心を落ち着ける時間を持てた。',
    ((CURRENT_DATE - 4) + TIME '23:50')::timestamptz
  ),
  (
    CURRENT_DATE - 3,
    '休日らしくゆっくりとした朝を過ごし、溜まっていた洗濯物を片付けられた。午後は資格試験のテキストを読み進めたが、思ったより理解に時間がかかったようだ。夕方は近所のカフェで気分転換をして、新しいメニューを楽しめたのが良いアクセントになった。',
    ((CURRENT_DATE - 3) + TIME '23:50')::timestamptz
  )
) AS v(diary_on, body, generated_at);

-- 確認用(投入結果を日付ごとに一覧表示) ------------------------------------

SELECT
  day::date AS diary_on,
  (SELECT COUNT(*) FROM posts p
     WHERE p.user_id = u.id
       AND p.posted_at >= day::timestamptz
       AND p.posted_at < (day::date + 1)::timestamptz) AS post_count,
  EXISTS (SELECT 1 FROM diaries d WHERE d.user_id = u.id AND d.diary_on = day::date) AS diary_exists
FROM generate_series(CURRENT_DATE - 6, CURRENT_DATE, INTERVAL '1 day') AS day
CROSS JOIN (SELECT id FROM users WHERE email = 'demo@tsumory.local') u
ORDER BY day;
