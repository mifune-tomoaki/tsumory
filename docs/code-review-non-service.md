# service層以外コードレビュー

`src/main/java/com/example/tsumory/`のうち`service/`パッケージを除いた全体(`config`/`controller`/`domain`/`form`/`repository`/`security`、および`TsumoryApplication`)と、`src/main/resources/`(`application.yaml`・Thymeleafテンプレート・Flywayマイグレーション)を対象に、[CLAUDE.md](../CLAUDE.md)の「コードレビューの観点」(可読性・保守性/エラーハンドリング/セキュリティ)でレビューした結果。

[code-review-service.md](./code-review-service.md)(service層レビュー)の対になるドキュメント。

## 重大な指摘

### 1. `application.yaml`のHibernateバインド値トレースログが、service層の機微情報マスキングを無効化している

**該当箇所**: `application.yaml:16-19`

```yaml
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
```

`PostService`/`DiaryService`は「本文には個人的な内容が含まれるためログには文字数のみ出力する」という明示的な方針でログ出力を書いている(`PostService.java:59`、`DiaryService.java:55`)。ところがこの設定はSQLのバインドパラメータ実値をTRACEレベルでログ出力するため、つぶやき本文・日記本文・メールアドレスなど、まさにservice層が意図的にマスクしていた内容がHibernateのログ経由でそのまま出力されてしまい、**service層側の配慮を丸ごと無効化している**。

さらにこの設定は`application-dev.yaml`のようなプロファイル限定ファイルではなく、**全環境共通の`application.yaml`**に直接書かれている。git logのコミットメッセージ(「Log SQL statements and bind parameter values for local dev」)からもローカル開発時のみを意図した設定だと分かるが、コード上はその意図を強制する仕組みがなく、このままの設定で万一本番相当の環境にデプロイされた場合、パスワードハッシュを含む全SQLバインド値がログに残り続けることになる。

**対応案**: `spring.config.activate.on-profile: dev`を持つ`application-dev.yaml`に切り出す、または`SPRING_PROFILES_ACTIVE`で明示的に有効化する運用にし、素の`application.yaml`には含めない。

## 可読性・保守性

### 2. 日記生成のオーケストレーション(AI呼び出し+永続化)がcontroller層にある

**該当箇所**: `DiaryController.java:96-108`(`generate`)

`DiaryController.generate()`が`diaryWriter.write(posts)`(AI呼び出し)→`diaryService.upsert(...)`(永続化)を直接呼び出しており、`DiaryService`自体は`DiaryWriter`に依存していない(`DiaryService.java`のコンストラクタ引数に`DiaryWriter`は含まれない)。CLAUDE.mdは「AI連携コードはservice配下に置く」としているが、AI呼び出し結果を使ったビジネスロジック(生成→保存という一連の処理)はcontroller層に置かれている状態。

現状はエントリポイントが1つ(このcontrollerメソッド)しかないため実害はないが、将来バッチ処理やAPIなど別の入口から同じ「生成して保存する」処理を呼びたくなった場合、この処理を複製することになる。`DiaryService`(または新設する`DiaryGenerationService`)に`generate(userId, date)`のようなメソッドとして寄せることを検討したい。

### 3. 全フォームで`_csrf`隠しフィールドを手動出力しているが、Thymeleafの自動注入と重複している可能性

**該当箇所**: `login.html:13-18`、`fragments/layout.html:20-25`(ログアウトフォーム)、`posts/home.html`(4箇所)、`diary/show.html:15-20`、`diaries/index.html:22-27`

依存関係に`thymeleaf-extras-springsecurity6`が含まれており、これが有効な状態で`th:action`を使うフォームには、SpringのCSRF保護用隠しフィールドが自動的に注入される。にもかかわらず、全テンプレートの全POSTフォームで`th:if="${_csrf != null}" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"`という隠しinputを手動でも出力しており、8箇所すべてで同じ定型文が繰り返されている。

動作を壊すものではない(同じトークン値なので実害はない)が、自動注入が効いているなら二重出力の冗長なマークアップになっている。一度実際に自動注入の有無を確認したうえで、手動出力を削除して定型文の重複を解消できないか検討する価値がある。

### 4. `PostCategory`(domain)にBootstrapのCSSクラス名が埋め込まれている

**該当箇所**: `PostCategory.java:7-18`(`badgeClass`フィールド)

```java
WORK("仕事", "bg-primary"),
PRIVATE("プライベート", "bg-success"),
...
```

`domain`パッケージはドメインモデル全般を置く場所だが、`badgeClass`はBootstrapという特定のUIフレームワークのCSSクラス名そのものであり、表示層の関心事がドメイン層に漏れ出している。プロジェクト規模的に実害は小さいが、将来Bootstrapのバージョンが上がってクラス名が変わる、あるいはAPI化してThymeleaf以外のフロントエンドを持つ、といった変化があった場合にドメインモデルまで変更が波及する。`controller`層(view/`PostView`まわり)にカテゴリ→CSSクラスの対応表を持たせる方が層の責務分離としては素直。

### 5. `spring.jpa.open-in-view`が未設定のまま(既定でtrue)

**該当箇所**: `application.yaml`(該当設定なし)

アプリ起動ログに`spring.jpa.open-in-view is enabled by default...`という警告が出ている通り、明示的な意思決定がされないままOSIVが有効になっている。本アプリはThymeleafのサーバーサイドレンダリングでビュー内での遅延ロードを許容する設計にも見えるが、意図してOSIVを使っているのか、単に未設定なだけなのかがコードから読み取れない。無効化してfetch戦略を明示するか、有効のままにするなら「意図して有効にしている」ことをコメントか設定コメントで残しておきたい。

### 6. `RequestLoggingContextFilter`がSpringのBeanではなく`new`で生成されている

**該当箇所**: `SecurityConfig.java:25`(`new RequestLoggingContextFilter()`)

CLAUDE.mdは「コンストラクタインジェクションを使う」ことを基本方針にしているが、このフィルタは`SecurityConfig`内で直接`new`されており、Spring管理のBeanになっていない。現状このフィルタは依存を持たないため実害はないが、将来何らかの依存(設定値やBeanへの参照)が必要になった際に、この生成方法のままだとDIができず作り直しが必要になる。`@Component`化して`SecurityFilterChain`に注入する形に統一しておくと一貫性が保てる。

## エラーハンドリング

### 7. `DiaryController.generate()`の`catch (Exception e)`が広すぎる

**該当箇所**: `DiaryController.java:101`

AI呼び出し(`diaryWriter.write`)や永続化(`diaryService.upsert`)で発生しうる想定内の失敗と、コーディングミスによる`NullPointerException`のような想定外の不具合を区別せず、どちらも同じ「日記の生成に失敗しました。時間をおいて試してください」というメッセージ・`log.warn`レベルでまとめて処理してしまう。想定外のバグが「AIがたまたま失敗しただけ」に見えてしまい、監視・アラートの観点で気づきにくくなる可能性がある。実害は小さいが、想定外例外は`log.error`にする、あるいはAI呼び出し由来の例外型を絞って`catch`するといった改善余地がある。

### 8. `DiaryController.list()`のページ番号が未検証

**該当箇所**: `DiaryController.java:59-73`(`list`)、`diaries/index.html:56-67`

`page`は`@RequestParam(defaultValue = "0") int page`をそのまま`PageRequest.of(page, PAGE_SIZE)`に渡している。負の値を渡すと`PageRequest.of`が`IllegalArgumentException`を投げ、ハンドラがないため既定のエラーページ(500)がそのまま表示される。テンプレート側の「前へ」リンクは`hasPrevious`のときのみ出るため通常操作では起きないが、URLを直接いじれば誰でも踏める。情報漏えいにはつながらない(スタックトレースは既定で非表示)ためセキュリティ上の深刻度は低いが、UXとしては`page < 0`を1ページ目に丸めるなどの防御的な処理を入れてもよい。

### 9. 良い点: 例外の握りつぶしがなく、意図が明確な例外処理になっている

**該当箇所**: `PostController.java:60-66`(`DailyPostLimitExceededException`のみを狙って`catch`)、`TsumoryUserDetailsService.java:23-27`(ログイン失敗時に理由をログへ)、`RequestLoggingContextFilter.java:40-44`(`finally`で`MDC.clear()`)

`PostController.create()`は上限超過例外だけを狙って`catch`しており、想定外の例外はそのまま伝播させて500になる(=バグの見逃しを避けられる)良い設計。`RequestLoggingContextFilter`も`finally`でMDCを確実にクリアしており、仮想スレッド環境でのMDC値の取り違え・リークを防いでいる。

## セキュリティ

### 10. 良い点: 認可は「拒否をデフォルト」にしたうえで、必要な箇所だけ許可している

**該当箇所**: `SecurityConfig.java:18-23`

```java
auth.requestMatchers("/login", "/webjars/**").permitAll().anyRequest().authenticated()
```

ログイン画面と静的リソース(WebJars)だけを明示的に許可し、それ以外は`anyRequest().authenticated()`でデフォルト拒否にしている。CSRF保護も無効化されておらず、CLAUDE.mdの禁止事項に反する箇所は見当たらない。

### 11. 良い点: パスワードハッシュがログや`toString()`に出力されないよう配慮されている

**該当箇所**: `TsumoryUserDetails.java:38-41`

`UserDetails`の実装で`toString()`を明示的にオーバーライドし、`passwordHash=***`とマスクしている。Lombokの`@Data`/`@ToString`を安易に使わず個別対応している点もCLAUDE.mdの方針(セキュリティ上重要なクラスでの`@Data`回避)に沿っている。

### 12. 良い点: `userId`はどのcontrollerでも`@AuthenticationPrincipal`由来で、クライアント指定を許していない

**該当箇所**: `PostController.java`、`DiaryController.java`全体

`postService`/`diaryService`に渡す`userId`はすべて`principal.getId()`から取得しており、リクエストパラメータやパス変数から`userId`を受け取る箇所は存在しない。他ユーザーのリソースを`userId`偽装で操作できる経路は見当たらない(所有者チェック自体はservice層の責務であり、[code-review-service.md](./code-review-service.md)側で確認済み)。

### 13. 良い点: リポジトリは全てSpring Data JPAの派生クエリで、文字列連結によるSQL組み立てがない

**該当箇所**: `PostRepository.java`、`UserRepository.java`、`DiaryRepository.java`

3つのリポジトリインターフェースはいずれもSpring Data JPAのメソッド名からのクエリ導出のみで構成されており、`@Query`によるネイティブSQLや文字列結合は一切ない。SQLインジェクションの経路は見当たらない。

### 14. デモユーザーのパスワードハッシュがマイグレーションにコミットされている点は許容範囲

**該当箇所**: `V4__seed_initial_user.sql`

平文パスワードではなくbcryptハッシュのみをコミットしており、コメントで「平文パスワードは別途開発者間で共有する」と明記されている。CLAUDE.mdの「パスワードを平文で保存しない」という方針には反しておらず、意図された設計として問題ない。ただし本番運用に進む際は、この開発用アカウントを無効化または削除する手順を用意しておきたい。

## まとめ

| # | 分類 | 深刻度 | 内容 |
|---|---|---|---|
| 1 | セキュリティ | 高 | `application.yaml`のHibernateバインド値トレースログが全環境で有効になっており、service層のログマスキング方針を無効化している |
| 2 | 可読性・保守性 | 中 | 日記生成のオーケストレーションがcontroller層にあり、service層の責務からはみ出している |
| 3 | 可読性・保守性 | 低 | CSRF隠しフィールドの手動出力とThymeleaf自動注入の重複疑い |
| 4 | 可読性・保守性 | 低 | `PostCategory`にBootstrap依存のCSSクラス名が漏れている |
| 5 | 可読性・保守性 | 低 | `open-in-view`が未設定のまま既定有効 |
| 6 | 可読性・保守性 | 低 | `RequestLoggingContextFilter`がBean化されていない |
| 7 | エラーハンドリング | 低 | `DiaryController.generate()`の`catch (Exception e)`が広すぎる |
| 8 | エラーハンドリング | 低 | ページ番号の未検証(負値で500) |

最優先は**指摘1**。service層のログ出力方針が実質的に無意味になっている状態なので、`application.yaml`のログ設定をプロファイル限定に切り出すことを推奨する。
