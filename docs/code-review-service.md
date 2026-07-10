# service層コードレビュー

`src/main/java/com/example/tsumory/service/` 配下のコードを対象に、[CLAUDE.md](../CLAUDE.md)の「コードレビューの観点」(可読性・保守性/エラーハンドリング/セキュリティ)でレビューした結果。

対象ファイル: `PostService`, `DiaryService`, `PostCategorizer`, `AnthropicPostCategorizer`, `DiaryWriter`, `AnthropicDiaryWriter`, `DailyPostLimitExceededException`, `ResourceNotFoundException`

## 重大な指摘

### 1. AIによる自動カテゴリ分類が本番では永続化されない(自己呼び出しによる`@Transactional`の無効化)

**該当箇所**: `PostService.java:92-101`(`triggerCategorization`)、`PostService.java:74-77`(`applyCategory`)

`triggerCategorization`は非同期タスク(仮想スレッド)の中で`applyCategory(postId, ...)`を呼び出しているが、これは同一クラス内からの**自己呼び出し(self-invocation)**になる。Spring AOPのプロキシ(トランザクション制御)は、Beanの外部からプロキシ経由で呼ばれた場合にのみ働くため、`this.applyCategory(...)`という形の呼び出しでは`applyCategory`に付けた`@Transactional`が一切適用されない。

その結果:

1. `postRepository.findById(postId)`はSpring Data JPAリポジトリ自身が持つ短命なトランザクションの中で実行され、戻り値の`Post`はそのトランザクション終了と同時に**デタッチ状態**になる。
2. 直後の`post.assignCategory(category)`はデタッチ済みエンティティのフィールドをメモリ上で書き換えるだけで、`save()`もフラッシュも行われないため、**DBには一切反映されない**。

つまり`create()`(`PostService.java:61`)や`edit()`(`PostService.java:70`)経由で呼ばれるAI分類は、例外も出さずに常に失敗した状態と同じ結果になる。`edit()`は本文編集時に一旦`category`を`null`にクリアしてから(`Post.edit`, `Post.java:54-57`)非同期で再分類を試みる設計だが、再分類側が機能しないため、**一度でも編集した投稿は永続的に未分類のままになる**。

既存のユニットテスト(`PostServiceTest`)はSpringコンテキストを使わず`new PostService(...)`で直接インスタンス化しているため、プロキシが存在せずこの問題を検知できない。`@SpringBootTest`または`@DataJpaTest`+実際のBean経由の呼び出しで検証しない限り気づけない。

**対応案**: `applyCategory`をトランザクション境界内で確実に呼ぶには、(a) 自己注入(`@Lazy`な自身のBean参照)経由で呼ぶ、(b) `applyCategory`を別のBean(例: `PostCategorizationService`)に切り出し、`PostService`から注入して呼ぶ、のいずれかで自己呼び出しを避ける。

### 2. 非同期分類の起動タイミングが早く、投稿のコミット前に実行され得る(上記1の副次問題)

**該当箇所**: `PostService.java:61`, `PostService.java:70`

上記1を仮に解消したとしても、`triggerCategorization`の呼び出しは`create()`/`edit()`という`@Transactional`メソッドの**内部**(コミット前)で行われている。`categorizationExecutor`は別スレッドで即座に実行を開始するため、呼び出し元の外側トランザクションがまだコミットされていないタイミングで`applyCategory`の`findById`が実行される可能性があり、その場合は対象の投稿行がまだ他コネクションから見えず`Optional.empty()`となって分類結果が失われる。

**対応案**: `TransactionSynchronizationManager.registerSynchronization(...)`、または`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`を使い、外側のトランザクションがコミットされた後に非同期タスクを起動するようにする。

## 可読性・保守性

### 3. `AnthropicPostCategorizer`と`AnthropicDiaryWriter`でAPI呼び出し・ロギングのボイラープレートが重複

**該当箇所**: `AnthropicPostCategorizer.java:51-64`、`AnthropicDiaryWriter.java:58-71`

「`System.nanoTime()`で計測開始 → `client.messages().create(params)` → `AnthropicException`をcatchして警告ログ+再スロー → 成功ログ(モデル名/経過時間/トークン数/requestId)」という十数行のパターンがほぼ同一のまま2箇所に存在する。両クラスの実処理(プロンプト組み立て・レスポンス解釈)は異なるが、呼び出し・計測・ロギング部分は共通化できる余地が大きい。今後Anthropic呼び出し箇所が増えるたびに同じボイラープレートが増殖しやすい。

**対応案**: 例えば`Message call(MessageCreateParams params, String logContext)`のような小さな共通ヘルパー(private static or 別クラス)に切り出す。

### 4. 投稿上限・ページサイズがコード内定数として埋め込まれている

**該当箇所**: `PostService.java:26`(`DAILY_POST_LIMIT = 50`)、`DiaryService.java:23`(`PAGE_SIZE = 20`)

同じserviceクラス内のAIモデル名・トークン数(`AnthropicPostCategorizer`/`AnthropicDiaryWriter`)は`@Value`で外部設定化されているのに対し、これらは`private static final`のハードコードのまま。運用中に調整したくなりやすい値(投稿上限、1ページの件数)なので、他の設定値と同様に`application.yaml`経由の設定にしておくと変更時にビルドが不要になる。

### 5. `PostService`内でpublicメソッドとprivateヘルパーが交互に並んでいる

**該当箇所**: `PostService.java` 全体構成

`create`/`edit`/`applyCategory`/`setCategory`(public)の後に`shutdownCategorizationExecutor`→`triggerCategorization`→`startOfDay`(private)が挟まり、その後にまた`delete`/`findOwnedPost`(public)が続く。読み手が「このクラスの公開APIは何か」を把握しづらいので、publicメソッドをまとめ、private ヘルパーは末尾にまとめる方が追いやすい。

## エラーハンドリング

### 6. 良い点: AI呼び出し失敗がユーザー操作をブロックしない設計になっている

**該当箇所**: `PostService.java:92-101`、`DiaryController.java:98-108`(参考)

`triggerCategorization`は`RuntimeException`を握りつぶしてログに残すのみで、投稿の作成・編集自体は正常に完了する設計になっている。指摘1・2の永続化バグとは別に、「AI呼び出しの失敗でつぶやき投稿自体が失敗する」という最悪のユーザー体験は避けられている点は良い設計判断。

### 7. AIのカテゴリ応答が想定外の値だった場合のフォールバックがない

**該当箇所**: `AnthropicPostCategorizer.java:76`

`PostCategory.valueOf(toolUse._input().convert(CategorizeInput.class).category())`は、ツール呼び出しのJSON Schemaで`enum`を4値に制限してはいるものの、モデルが万一スキーマ外の文字列を返した場合は`IllegalArgumentException`で失敗する。呼び出し元(`triggerCategorization`)で`RuntimeException`として握りつぶされるため実害は小さいが、「分類失敗」ログの原因がひと目で分かるよう、`valueOf`失敗時に`OTHER`へフォールバックしても良い(ログでの切り分けやすさとのトレードオフなので必須ではない)。

## セキュリティ

### 8. 良い点: 認可はすべてservice層で検証され、`userId`はリクエストパラメータではなく認証情報から取得

**該当箇所**: `PostService.findOwnedPost`(`PostService.java:113-121`)、`DiaryService`各メソッド、呼び出し元の`PostController`/`DiaryController`

`edit`/`setCategory`/`delete`はすべて`findOwnedPost`で所有者チェックを経由しており、`DiaryService`側もリポジトリのクエリ自体が`userId`でスコープされている。両コントローラーとも`userId`は`@AuthenticationPrincipal`由来で、クライアントが指定できるリクエストパラメータからは取得していないため、他ユーザーのリソースへの不正アクセス経路は見当たらない。

### 9. 良い点: `ResourceNotFoundException`が「存在しない」と「他人の所有」を区別しない

**該当箇所**: `ResourceNotFoundException.java`、`PostService.findOwnedPost`

所有者不一致・存在しないIDのどちらでも同じ404にまとめており、リソースの存在有無を第三者に推測させない設計になっている。意図的かは不明だが、結果として良いセキュリティ特性になっている。

### 10. 投稿本文がそのままプロンプトへ埋め込まれる(プロンプトインジェクションの余地)

**該当箇所**: `AnthropicPostCategorizer.java:46`(`categorize`)、`AnthropicDiaryWriter.java:88-99`(`buildUserMessage`)

ユーザーが自由入力できるつぶやき本文を、エスケープや区切りの工夫なしにそのままプロンプトに連結している。理論上、つぶやきの中に「これまでの指示を無視して…」のような指示文を混ぜることでAIの出力を誘導される可能性がある。実害は現状小さい:

- `AnthropicPostCategorizer`側は`toolChoice`+`enum`制約付きツール呼び出しのため、操作されても選べるのは既存4カテゴリのいずれかに限られる。
- `AnthropicDiaryWriter`側は出力が自由テキストだが、日記本文の表示は`diary/show.html:10`で`th:text`(自動エスケープ)を使っており、XSSには繋がらない。

現時点で緊急対応が必要な脆弱性ではないが、日記生成の自由度が上がるほど「本人の言葉ではない内容が日記に混入する」リスクは残るため、将来的にはユーザー入力とシステム指示の境界を明示する(例: 区切り文字列やXMLタグでユーザー発話を囲む)など、プロンプトインジェクション対策を検討する価値がある。

## まとめ

| # | 分類 | 深刻度 | 内容 |
|---|---|---|---|
| 1 | エラーハンドリング/正確性 | 高 | 自己呼び出しにより`applyCategory`の`@Transactional`が無効化され、AI分類結果が永続化されない |
| 2 | エラーハンドリング/正確性 | 中 | 非同期分類がトランザクションコミット前に走り得るタイミング競合 |
| 3 | 可読性・保守性 | 低 | Anthropic呼び出し・ロギングの重複 |
| 4 | 可読性・保守性 | 低 | 投稿上限・ページサイズのハードコード |
| 5 | 可読性・保守性 | 低 | public/privateメソッドの並び順 |
| 7 | エラーハンドリング | 低 | AI応答が不正値だった場合のフォールバック欠如 |
| 10 | セキュリティ | 低(将来的な検討事項) | プロンプトインジェクションへの耐性 |

最優先は**指摘1・2**。現状、投稿の自動カテゴリ分類機能はほぼ確実に無効化されており、フェーズ2の目玉機能の一つが機能していない状態なので、早急な修正を推奨する。
