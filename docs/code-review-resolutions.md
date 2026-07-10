# コードレビュー指摘の解消メモ

`code-review-service.md`/`code-review-non-service.md`で挙げた指摘のうち、対応済みのものをここに記録する。指摘そのものの記述は元のレビュードキュメント側にも残し、本ファイルでは「何を・なぜ・どう直したか・どう検証したか」を追記する。

## [code-review-non-service.md](./code-review-non-service.md) 指摘1: Hibernateバインド値トレースログが全環境で有効だった

### 何が問題だったか

`application.yaml`に`org.hibernate.orm.jdbc.bind: trace`がプロファイル限定なしで書かれており、`PostService`/`DiaryService`が「本文は文字数のみログに出す」と意図的にマスクしている内容(つぶやき本文・日記本文・メールアドレスなど)が、Hibernateのバインド値ログ経由でそのまま出力されてしまう状態だった。ローカル開発用の意図(git logコミットメッセージより)はあったが、それを強制する仕組みがなく、素の`application.yaml`に書かれていたため、本番相当環境にそのままデプロイされた場合も有効になってしまう構成だった。

### どう直したか

1. `org.hibernate.SQL: debug`/`org.hibernate.orm.jdbc.bind: trace`を`application.yaml`から削除。
2. `spring.config.activate.on-profile: dev`で明示的にスコープした`application-dev.yaml`を新設し、そちらに移設。
3. `build.gradle.kts`の`bootRun`タスクに`environment("SPRING_PROFILES_ACTIVE", "dev")`を追加。`./gradlew bootRun`でローカル起動する限りは今まで通りSQL/バインド値ログが出る一方、jarを直接実行する・別プロファイルで起動するなど`bootRun`タスクを経由しない起動では、明示的に`dev`プロファイルを指定しない限りこのログは出なくなる。

### どう検証したか

- `src/test/java/com/example/tsumory/config/LoggingConfigurationTest.java`を追加し、以下を自動テストで固定化:
  - `application.yaml`(プロファイル無指定時の基底設定)には`org.hibernate.SQL`/`org.hibernate.orm.jdbc.bind`のログレベル設定が**存在しない**こと。
  - `application-dev.yaml`は`spring.config.activate.on-profile: dev`でスコープされたうえで、該当のログレベル設定を持つこと。
  - 誰かが将来うっかり`application.yaml`に同じ設定を書き戻した場合、このテストが落ちて気づけるようにする回帰防止の位置づけ。
- 実際に`./gradlew bootRun`を実行し、起動ログに`The following 1 profile is active: "dev"`が出ることを目視確認済み(`build.gradle.kts`の変更が意図通り効いていることの確認)。
- `./gradlew test`で全テスト(既存分含む)がパスすることを確認済み。

### 影響範囲・残課題

- ローカル開発体験(`./gradlew bootRun`でのSQLログ)は変更なし。
- gradle経由ではない起動方法(パッケージ済みjarの直接実行、コンテナイメージなど)で明示的に`SPRING_PROFILES_ACTIVE=dev`(またはそれに準ずるプロファイル)を指定しない限り、バインド値トレースログは出力されない。これは意図した挙動(secure by default)。
- 今後、本番相当環境向けの`application-prod.yaml`のようなプロファイルを追加する場合も、この`application-dev.yaml`と同じ「on-profileで明示スコープする」パターンを踏襲すること。
