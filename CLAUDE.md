# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

tsumoryは、ユーザーの短い投稿(「つぶやき」)をAIが日記としてまとめてくれるサービス。AIによる要約・生成にはAnthropic Java SDK(`com.anthropic:anthropic-java`)を利用する想定。

現状のコードベースはSpring Initializrで生成した直後の雛形のみで、`src/`配下には`TsumoryApplication`とそのコンテキストロードテストしか存在しない。ドメインモデル、コントローラー、リポジトリなどはまだ実装されていない。

企画・仕様ドキュメントの索引は`docs/spec.md`。コンセプト(`docs/mymanifesto.md`)、開発フェーズ(`docs/phases.md`)、フェーズ1の実装仕様(`docs/phase1-spec.md`)を参照。

## コマンド

Gradle(Kotlin DSL)プロジェクトで、Java 21を対象とする(toolchainで固定。`gradle.properties`でもローカルJDK 21のパスを`org.gradle.java.home`に固定している)。

```bash
# ビルド(Spotlessによる整形が先に走る — 下記参照)
./gradlew build

# アプリの起動
./gradlew bootRun

# 全テストの実行
./gradlew test

# 単一のテストクラスを実行
./gradlew test --tests "com.example.tsumory.TsumoryApplicationTests"

# 単一のテストメソッドを実行
./gradlew test --tests "com.example.tsumory.TsumoryApplicationTests.contextLoads"

# Spotlessでコードを整形(JavaはGoogle Java Format、*.gradle.ktsはktlint)
./gradlew spotlessApply

# フォーマットをチェックのみ(変更はしない)
./gradlew spotlessCheck
```

`compileJava`は`spotlessApply`に依存しているため、`./gradlew build`/`bootRun`/`test`のいずれを実行してもコンパイル前にJavaソースと`*.gradle.kts`が自動整形される。通常のワークフローで別途フォーマットコマンドを叩く必要はない。

### ローカルインフラ

`compose.yaml`にローカル開発用の依存サービスを定義しており、ローカルでアプリを起動するとSpring BootのDocker Compose連携(`spring-boot-docker-compose`、`developmentOnly`)により自動起動する。

- `postgres`(postgres:16-alpine)— DB名`tsumory`、ユーザー`myuser` — `5432`番で公開
- `pgAdmin` — `5050`番で公開

## アーキテクチャ / 技術スタック

`build.gradle.kts`の主要な依存関係と、それが実装方針に与える示唆:

- **Web層**: `spring-boot-starter-web` + `spring-boot-starter-thymeleaf` — サーバーサイドレンダリングのMVC構成でThymeleafテンプレートを使う(REST専用・SPA構成ではない)。`thymeleaf-extras-springsecurity6`もあるため、テンプレート内でセキュリティを考慮した表示制御(`sec:authorize`など)が可能。
- **フロントエンド資材**: Bootstrap 5はWebJars(`org.webjars:bootstrap`)経由で導入し、`org.webjars:webjars-locator-lite`によりテンプレート内でバージョン番号なしにリソース解決できる(例: `@{/webjars/bootstrap/css/bootstrap.min.css}`)。
- **永続化**: `spring-boot-starter-data-jpa` + `postgresql`ドライバ。`flyway-core` / `flyway-database-postgresql`によりスキーママイグレーションを管理するため、テーブルやカラムの追加は`ddl-auto`ではなくFlywayマイグレーションとして行う。
- **認証**: `spring-boot-starter-security`は依存関係として入っているが未設定。`SecurityFilterChain`はまだ存在しない。
- **バリデーション**: `spring-boot-starter-validation`によりリクエスト/フォームオブジェクトにBean Validationを適用する。
- **並行処理**: 仮想スレッドが有効化されている(`application.yaml`の`spring.threads.virtual.enabled: true`)ため、リクエスト処理中のブロッキングI/Oは仮想スレッド上で実行される。通常のリクエスト処理でプラットフォームスレッドを手動プーリングする必要はない。
- **AI連携**: `com.anthropic:anthropic-java`がClaudeを呼び出して投稿から日記を生成・要約するためのSDK。AI連携コードは`service/`配下に置く(専用パッケージには分けない)。
- **コードスタイル**: Spotlessにより全JavaソースにGoogle Java Format、Gradle Kotlin DSLファイルにktlintが適用される。ビルド時に自動実行される(上記コマンド参照)。

## ドメインモデル概要

中核となるエンティティは次の3つで、いずれも`domain/`配下に置く。

- **User**: ログイン認証を持つユーザー。
- **Post(つぶやき)**: ユーザーが投稿する短文。
- **Diary(日記)**: その日のPostをAIがまとめて生成する日記。**ユーザーごとに1日1本**が核となる制約。

フィールドの詳細・ER図・テーブル設計・命名規則は`docs/phase1-spec.md`を正とする(索引は`docs/spec.md`)。実装が進むにつれて変わりうる情報のため、ここでは重複を避けて詳細を持たない。

## ディレクトリ構成

```
src/main/java/com/example/tsumory/
├── TsumoryApplication.java
├── config/       # @Configurationクラス(Security、Web/MVC、Anthropic SDKクライアント設定など)
├── controller/   # @Controller / @RestController — リクエスト処理、Thymeleafへのモデル受け渡し
├── service/      # ビジネスロジック。repositoryの呼び出しやAnthropic SDK連携(AI連携)もここに含む
├── repository/   # Spring Data JPAリポジトリ
├── domain/       # ドメインモデル全般(JPAの@Entityクラスを含む)
├── form/         # Thymeleafのフォーム入力に特化したフォームオブジェクト(Bean Validationで検証)
└── security/     # SecurityFilterChain、UserDetailsServiceなど認証関連コンポーネント

src/main/resources/
├── application.yaml
├── db/migration/  # Flywayマイグレーションスクリプト(V1__xxx.sql など)— まだ未作成
├── templates/     # Thymeleafテンプレート — まだ未作成
└── static/        # 静的アセット(WebJarsと併用)— まだ未作成
```

パッケージは機能(post/diary/authなど)ごとではなく、技術的な役割ごとに分ける(package-by-layer)方針。新しいクラスは上記構成に沿って`controller` / `service` / `repository` / `domain` / `form`のいずれかに配置する。

`domain`はJPAの`@Entity`に限らず、ドメインモデル全般を置く場所。`form`はThymeleafのフォーム入力専用で、フォームに紐づかないリクエスト/レスポンス形式(将来API化した場合など)はここには含めない。

## コーディング規約・禁止事項

### Java 21の活用

- **record**を積極的に使う。特に`form/`配下のフォーム入力オブジェクトや、イミュータブルな値の受け渡しに向いている。ただしJPAの`@Entity`は可変・引数なしコンストラクタが必要なため通常のクラスのまま実装する。
- **switch式・パターンマッチング**(`instanceof`パターン、switchのパターンマッチング)を条件分岐に活用する。
- **テキストブロック**(`"""`)をAIへのプロンプトや複数行SQLなど複数行文字列に使う。
- **var**による型推論は、右辺から型が自明な場面で使う。
- Sequenced Collections API(`SequencedCollection`など)を順序付きコレクション操作に活用する。
- **仮想スレッド**が有効化されている前提でコードを書く。リクエスト処理を自前のスレッドプールでラップしない(`Executors.newFixedThreadPool`などは避け、必要なら`Executors.newVirtualThreadPerTaskExecutor()`を使う)。`synchronized`ブロック/メソッドは仮想スレッドのピンニングを引き起こすため、ロックが必要な箇所では`ReentrantLock`など`java.util.concurrent.locks`側を優先する。

### DIパターン

- **コンストラクタインジェクション**を使う。フィールドへの`@Autowired`やセッターインジェクションは使わない。
- 依存フィールドは`private final`にし、Lombokの`@RequiredArgsConstructor`でコンストラクタを生成するのを基本形とする。
- 実装が1つしかないインターフェースを不要に増やさない。差し替えの必要が具体的にない限り、`service`は具象クラスを直接注入してよい。

### Lombokの使い方

- コンストラクタインジェクション用に`@RequiredArgsConstructor`を使う。
- `@Data`はクラス全体に付与しない。特にJPAの`@Entity`では`equals`/`hashCode`/`toString`が遅延ロードや双方向関連と相性が悪いため使わない。必要なアクセサだけ`@Getter`/`@Setter`を個別に(またはクラスに`@Getter`のみ)付与する。
- `@Builder`は生成パラメータが多いdomain/formオブジェクトなど、必要な箇所に限定して使う。
- ロガーは`@Slf4j`で用意する(手動で`LoggerFactory.getLogger`を書かない)。
- `@ToString`/`@EqualsAndHashCode`をJPAエンティティの関連フィールド(`@OneToMany`など)に含めない(循環参照・N+1の原因になるため)。

### フォーマッタ方針

- フォーマットはSpotless(Java: Google Java Format、`*.gradle.kts`: ktlint)に一任する。手動でインデントや改行を調整しない。
- `compileJava`が`spotlessApply`に依存しているため、通常のビルド(`./gradlew build`など)で自動整形される。編集後に整形だけ確認したい場合は`./gradlew spotlessApply`を実行する。
- Spotlessの設定(`build.gradle.kts`内の`spotless {}`ブロック)やGoogle Java Formatのスタイルには手を加えない。

### 全般の禁止事項

- `ddl-auto`によるスキーマ自動生成は使わない。スキーマ変更は必ずFlywayマイグレーションで行う。
- 一度適用済みのマイグレーションファイル(`V1__xxx.sql`など)は編集しない。スキーマ変更は常に新しいバージョンのマイグレーションファイルを追加して行う(Flywayはチェックサムで既存ファイルの変更を検知し、適用済み環境でエラーになるため)。
- パッケージを機能単位(`post`/`diary`/`auth`など)で分割しない。技術的役割(`controller`/`service`/`repository`/`domain`/`form`)で分ける。

### セキュリティ上の禁止事項

- パスワードを平文で保存しない。必ず`PasswordEncoder`(BCryptなど)でハッシュ化する。
- APIキーやDB接続情報などの秘密情報をコードや設定ファイルにハードコードしない・コミットしない。環境変数やSpringの設定プロパティ経由で注入する。
- ユーザー入力を信頼しない。フォームは必ずBean Validationでサーバー側検証を行う(クライアント側検証のみに頼らない)。
- 認可チェックを画面表示の出し分け(UIの非表示)だけに頼らない。controller/service層で必ず認可を検証する。
- ログにパスワード・認証トークン・Anthropic APIキーなど機微情報を出力しない。
- CSRF保護を安易に無効化しない。無効化が必要な場合は理由をコメントで明記する。
- SQLを文字列連結で組み立てない。JPA/パラメータバインディングを使う。

## コードレビューの観点

PRレビュー・セルフレビュー時は次の3点を優先的に確認する。

### 可読性・保守性

- クラス名・メソッド名がドメイン用語(つぶやき/日記/カテゴリなど)や責務を正確に表しているか。
- パッケージが技術的役割(`controller`/`service`/`repository`/`domain`/`form`)に沿って配置されているか、機能単位(post/diary/authなど)で分割されていないか。
- 1メソッドがバリデーション・永続化・ログ出力・非同期処理などを過度に混在させていないか。
- Java 21の機能(record、switch式、テキストブロックなど)を活用し、冗長な実装になっていないか。
- 不要なコメント(コードを読めば分かるWHAT)がなく、必要なコメント(WHYや制約)が書かれているか。
- マジックナンバー・マジック文字列が定数化されているか(例: `DAILY_POST_LIMIT`のような命名)。

### エラーハンドリング

- ユーザー起因のエラー(バリデーション違反、投稿上限超過、所有者不一致など)とシステム起因のエラー(AI呼び出し失敗など)を区別して扱えているか。
- 例外は`@ResponseStatus`を持つ専用例外(`DailyPostLimitExceededException`、`ResourceNotFoundException`など)に変換されているか。汎用的な`RuntimeException`をそのまま投げていないか。
- 非同期処理(投稿のAIカテゴリ分類など)の失敗がユーザーの操作(投稿・編集そのもの)をブロック・失敗させていないか。失敗時にログへ記録されているか。
- 例外を握りつぶして原因が追えなくなっていないか(catchしたら必ずログを残すか再スローする)。
- 外部サービス(Anthropic API)のタイムアウト・エラー時に、UIやユーザー体験が破綻しないか。

### セキュリティ

- 認可チェックがcontroller/service層で行われているか(UIの出し分けだけに頼っていないか)。他人のPost/Diaryへのアクセスは所有者チェック(`findOwnedPost`など)を経由しているか。
- フォーム入力にBean Validationが付与されているか(クライアント側検証のみに頼っていないか)。
- パスワードが平文で扱われていないか(`PasswordEncoder`経由か)。
- ログに本文・パスワード・認証トークン・APIキーなど機微情報を出力していないか(文字数のみ出力するなどの配慮があるか)。
- SQLを文字列連結で組み立てていないか(JPA/パラメータバインディングを使っているか)。
- CSRF保護を安易に無効化していないか(無効化する場合は理由がコメントされているか)。
- 秘密情報がコード・設定ファイルにハードコードされていないか(環境変数・Springの設定プロパティ経由か)。

## 設定

現時点の設定ファイルは`src/main/resources/application.yaml`のみ(`spring.application.name`、仮想スレッド設定)。アプリの成長に応じてDB接続情報やAnthropic APIキーなどを追加する際は、ハードコードせず環境変数やSpringの設定プロパティ経由で管理する。
