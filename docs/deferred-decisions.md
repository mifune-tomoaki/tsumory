# 保留した設計判断

コードレビューや設計議論の中で、「今は対応しないが、将来ある条件を満たしたら再検討する」と決めた事項を記録する。対応することになったら、このファイルから該当エントリを削除し、対応内容は`docs/code-review-resolutions.md`などしかるべき場所に記録する。

## Post/Diaryのコンストラクタ・ミューテータに自前の不変条件チェックが無い

**保留日**: 2026-07-10
**関連**: [code-review-resolutions.md](./code-review-resolutions.md)「Diary.bodyにNotBlankが無かった件」の残課題

### 経緯

`docs/code-review-service.md`指摘10(投稿本文がそのままプロンプトへ埋め込まれる)への対応として、`Post.bodyForPrompt()`をドメイン層に追加し、無害化の責務をservice層からdomain層へ移した([解消メモ参照](./code-review-resolutions.md))。この対応の後、「同じ理屈で、ドメイン層で他に漏れているチェックはないか」を洗い出す追加監査を行った。

その監査で、`Post`(`Post(User, String, Instant)`)/`Diary`(`Diary(User, LocalDate, String, Instant)`)のコンストラクタや、`edit`/`regenerate`のようなミューテータが、null・空文字・長さ超過などの不変条件を自前でチェックしていないことが分かった。唯一の防御はJPAエンティティに付与したBean Validationアノテーション(`Post.body`の`@NotBlank`/`@Size`、追加対応した`Diary.body`の`@NotBlank`)であり、これはHibernateがflush(実際にINSERT/UPDATEを発行するタイミング)時にのみ働く。つまり、`Post`/`Diary`を構築してから実際に永続化されるまでの間は、無効な状態のオブジェクトが一時的に存在しうる。

この監査では次の3案を並べて検討した。

1. `Diary.body`に`@NotBlank`を追加する。
2. `Post`/`Diary`のコンストラクタ・ミューテータに自前のガード(`Objects.requireNonNull`など)を追加する。
3. 何もせず、発見事項として記録するだけにする。

1は実害のある明確な不具合(AIが空応答を返すと空の日記が気づかれず保存される)だったため対応済み。2は「現状バイパスする経路が実在しない」という理由で保留とし、本ドキュメントに議論を残すことにした。

### 現状バイパス経路が無いと判断した根拠

- `PostForm`は`@NotBlank @Size(max = 100)`で検証済みであり、`PostController.create`/`edit`はいずれも`@Valid`でこれを通してから`PostService`を呼ぶ。
- `PostService.create`/`edit`、`DiaryService.upsert`以外に`Post`/`Diary`を組み立てて永続化する経路は現状存在しない(controller/service層を確認済み)。
- したがって現時点でのこの指摘は「将来のリスクに備えた防御的な提案」であり、今すぐ直さないと壊れる具体的なバグではない。

### 議論を再開すべき条件

以下のいずれかに当てはまったら、この議論を再開し、コンストラクタ/ミューテータへの自前ガード追加を具体的に検討する。

- `PostForm`/`DiaryController`を経由せずに`Post`/`Diary`を組み立てる新しい経路(バッチ処理、管理画面、外部API連携、CLIツールなど)が追加されるとき。
- 永続化(`save`)を経る前に`Post`/`Diary`のインメモリ状態を使う処理が増えるとき(例: 保存前にAIプロンプトを組み立てる、保存前にバリデーション結果を先読みして画面に返す、など)。
- Bean Validationがflush時にしか発火しないことに起因する不具合が一度でも実際に観測されたとき。
- `Post`/`Diary`を直接`new`するコードがコードベース中に複数箇所現れるようになったとき(単一箇所からしか呼ばれない前提が崩れるとき)。

## User.email/passwordHashにBean Validationが無い

**保留日**: 2026-07-10
**関連**: [code-review-resolutions.md](./code-review-resolutions.md)「Diary.bodyにNotBlankが無かった件」の残課題

### 経緯

上記と同じ監査で、`User`エンティティ(`User.java`)には`email`/`passwordHash`いずれにもBean Validationのアノテーションが一切付いていないことが分かった。

### 現状バイパス経路が無いと判断した根拠

- フェーズ1の実装スコープにユーザー登録UI/APIが含まれておらず(`docs/phase1-spec.md`参照)、`User`を作る唯一の経路は`V4__seed_initial_user.sql`によるシード投入のみ。
- Flywayマイグレーションで投入する値は開発者自身が管理しているため、実質的にユーザー入力を経由しない。

### 議論を再開すべき条件

- ユーザー登録機能(サインアップUI/API)を実装するとき。その際、対応するフォーム(例: `SignupForm`)のBean Validationと合わせて、`User`エンティティ側にも`@NotBlank`/`@Email`などを追加することを検討する。
