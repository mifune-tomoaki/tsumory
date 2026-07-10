# ドメインテスト群のリファクタリング指摘

`src/test/java/com/example/tsumory/domain/`配下(`PostTest`/`DiaryTest`/`PromptSanitizerTest`)を対象に、重複・一貫性・カバレッジの観点でレビューした結果。コードレビュー([code-review-service.md](./code-review-service.md)など)と同様、指摘のみを記録し、対応は別途行う。

## 1. `Post`用のファクトリヘルパーが`service`層のテストと一字一句同じ内容で重複している

> **✅ 対応済み**: [code-review-resolutions.md](./code-review-resolutions.md#refactor-testsmd-指摘1-postファクトリヘルパーの重複)を参照。

**該当箇所**: `PostTest.java:16-18`(`post(String body)`)、`src/test/java/com/example/tsumory/service/AnthropicPostCategorizerTest.java:18-20`(同名の`post(String body)`)

```java
private Post post(String body) {
  return new Post(TestFixtures.user(), body, Instant.now());
}
```

この private ヘルパーが、`domain`パッケージの`PostTest`と`service`パッケージの`AnthropicPostCategorizerTest`に一字一句同じ内容で存在している。`new Post(TestFixtures.user(), body, ...)`という組み立て自体は`PostServiceTest`(6箇所)や`AnthropicDiaryWriterTest`にも繰り返し登場しており、`Post`のテスト用ファクトリが各テストクラスにローカルで再発明されている状態。

**対応案**: `TestFixtures`(`support`パッケージ)に`public static Post post(String body)`を追加し、各テストクラスのローカルヘルパーを削除してこちらに寄せる。`TestFixtures.user()`と同じ扱いにする。

## 2. `DiaryTest`には同種のファクトリヘルパーが無く、組み立てロジックがテストメソッド内に重複している

**該当箇所**: `DiaryTest.java:39`、`DiaryTest.java:48`

```java
Diary diary = new Diary(TestFixtures.user(), LocalDate.now(), "", Instant.now());
...
Diary diary = new Diary(TestFixtures.user(), LocalDate.now(), "今日は良い一日だった", Instant.now());
```

`PostTest`は`post(String body)`という1行ヘルパーを用意しているのに、`DiaryTest`は同じ形の組み立てをテストメソッドごとに直接書いている。2箇所とはいえ、`PostTest`との書き方の非対称性が気になる。

**対応案**: 指摘1で`TestFixtures.post(String body)`を追加するなら、対称性のために`TestFixtures.diary(String body)`(ユーザー・日付・生成時刻は固定値、本文だけ受け取る)も合わせて追加し、`DiaryTest`側もそちらを使う形に揃える。

## 3. `Instant.now()`/`LocalDate.now()`を使っており、プロジェクト内の「固定クロック」規約から外れている

**該当箇所**: `PostTest.java:17`、`DiaryTest.java:39,48`(`AnthropicPostCategorizerTest.java:19`も同様)

`PostServiceTest`/`DiaryServiceTest`/`AnthropicDiaryWriterTest`は`TestFixtures.fixedClock()`(`Instant.parse("2026-07-10T03:00:00Z")`に固定)を通してテスト全体で日時を決定的にしている。一方`PostTest`/`DiaryTest`(と`AnthropicPostCategorizerTest`)だけが実時刻の`Instant.now()`/`LocalDate.now()`を使っており、テストスイート内で日時の扱い方が二重基準になっている。

現状はどのテストも日時の値そのものをアサーションしていないため実害はないが、他のテストと同じ規約(`TestFixtures.NOW`)に揃えることで、将来日時を絡めたアサーションを追加しやすくなり、テストの決定性についての認知コストも下がる。

**対応案**: `Instant.now()`→`TestFixtures.NOW`、`LocalDate.now()`→`TestFixtures.NOW.atZone(TestFixtures.ZONE).toLocalDate()`(または`TestFixtures`に`LocalDate today()`のようなヘルパーを追加)に置き換える。

## 4. `DiaryTest`が既存のサンプル本文定数(`TestFixtures.DIARY_BODY_DRAFT`など)を使わず、その場限りの文字列を書いている

**該当箇所**: `DiaryTest.java:48`(`"今日は良い一日だった"`)

`DiaryServiceTest`はすでに`TestFixtures.DIARY_BODY_DRAFT`/`DIARY_BODY_REGENERATED`という「リアルな日本語のサンプル日記本文」を使い回しているが、`DiaryTest`はそれらを使わず新たに`"今日は良い一日だった"`という文字列をその場で書いている。内容自体は今回のテストの主旨(空文字でなければ違反しない)には影響しないが、既存の共通データを使わない理由が特に無い。

**対応案**: `"今日は良い一日だった"`を`TestFixtures.DIARY_BODY_DRAFT`に置き換える。

## 5. `DiaryTest`のテストメソッド名だけ、プロジェクトの命名規約(`メソッド名_条件`)から外れている

**該当箇所**: `DiaryTest.java:38`(`rejectsBlankBody`)、`DiaryTest.java:47`(`acceptsNonBlankBody`)

このテストスイート全体(`PostServiceTest`/`DiaryServiceTest`/`PostTest`/`PromptSanitizerTest`など)は`対象メソッド_期待する振る舞い`という命名(例: `sanitize_neutralizesAngleBrackets`、`bodyForPrompt_leavesOrdinaryBodyUnchanged`)で統一されているが、`DiaryTest`の2メソッドだけこの接頭辞が無い。

**対応案**: 検証対象は`Diary.body`に付与した`@NotBlank`制約なので、例えば`bodyValidation_rejectsBlankBody`/`bodyValidation_acceptsNonBlankBody`のように、他クラスと同じ命名パターンに揃える。

## 6. `TestFixtures`のクラスコメントが実態と合わなくなっている

**該当箇所**: `src/test/java/com/example/tsumory/support/TestFixtures.java:8`

```java
/** service層のユニットテストで使い回す固定クロックとサンプルデータの生成ヘルパー。 */
```

`TestFixtures`は当初`PostServiceTest`/`DiaryServiceTest`(service層)のために作られたが、現在は`domain`パッケージの`PostTest`/`DiaryTest`からも直接使われている。コメントが「service層の」と限定してしまっており、実態(domain層のテストからも使われる共通ヘルパー)と食い違っている。

**対応案**: コメントから「service層の」という限定を外し、「テスト全体で使い回す」のように書き換える。

## 7. `Post.body`のBean Validation制約(`@NotBlank`/`@Size(max = 100)`)には専用テストが無く、`Diary.body`と比べてカバレッジが非対称

**該当箇所**: `PostTest.java`全体(該当テストなし)、比較対象は`DiaryTest.java:37-43`

`DiaryTest`は`Diary.body`の`@NotBlank`を`jakarta.validation.Validator`で直接検証するテストを持っている一方、`Post.body`に付与されている`@NotBlank`/`@Size(max = 100)`(`Post.java:35-36`)には同等のテストが無い。`PostTest`は`bodyForPrompt()`の振る舞いのみを検証しており、エンティティ自身の制約は検証対象になっていない。

**対応案**: `DiaryTest`と同じ要領で、`Post`にも「空文字は`ConstraintViolation`になる」「101文字は`ConstraintViolation`になる」「100文字以内の通常の本文は違反にならない」を検証するテストを追加し、`Diary`側とカバレッジの粒度を揃える。

## 8. (7を採用する場合の付随指摘) `ValidatorFactory`/`Validator`のセットアップがクラスごとに重複しうる

**該当箇所**: `DiaryTest.java:24-33`(`@BeforeAll`/`@AfterAll`)

指摘7で`Post`にも同種のBean Validationテストを追加すると、`Validation.buildDefaultValidatorFactory()`まわりの`@BeforeAll`/`@AfterAll`セットアップが2クラスに重複することになる。現時点(`DiaryTest`1クラスのみ)では抽象化するほどではないが、2クラス目ができた時点で共通化を検討する。

**対応案**: 今は据え置き。`Post`用のBean Validationテストを追加するタイミングで、`support`パッケージに小さな共有ヘルパー(例: `TestFixtures.validate(Object)`、または`Validator`を返すユーティリティ)を切り出すか、その時点で改めて判断する。

## 9. `PromptSanitizerTest`と`PostTest`で、区切りタグ偽装への耐性が2箇所でほぼ同じ内容をテストしている

**該当箇所**: `PromptSanitizerTest.java:20-26`(`sanitize_neutralizesFakeDelimiterTags`)、`PostTest.java:28-33`(`bodyForPrompt_neutralizesForgedDelimiterTagsRegardlessOfHowThePostWasBuilt`)

`Post.bodyForPrompt()`は`PromptSanitizer.sanitize(body)`を呼ぶだけの1行の委譲なので、両テストはほぼ同じロジックを別の入口(ユーティリティ関数そのもの／ドメインオブジェクト経由)から検証している。意図的な重複(ユニットレベルの検証と、委譲が正しく配線されていることの契約レベルの検証)だと考えられるが、その意図がコード上に書かれていない。

**対応案**: 修正は不要。ただし、なぜ2箇所でテストしているかが分かるよう、どちらかのテストに一言コメントを添えておくと、将来「重複している」と誤解されて片方が削除されるのを防げる。

## まとめ

| # | 内容 | 対応の要否 |
|---|---|---|
| 1 | `Post`用ファクトリヘルパーが`domain`/`service`テストで一字一句重複 | ✅対応済み |
| 2 | `DiaryTest`に同種のファクトリヘルパーが無い | 対応推奨(1とセット) |
| 3 | `Instant.now()`/`LocalDate.now()`が固定クロック規約から外れている | 対応推奨 |
| 4 | `DiaryTest`が既存のサンプル本文定数を使っていない | 対応推奨(小さい) |
| 5 | `DiaryTest`のテストメソッド名だけ命名規約から外れている | 対応推奨(小さい) |
| 6 | `TestFixtures`のクラスコメントが実態と不一致 | 対応推奨(小さい) |
| 7 | `Post.body`のBean Validation制約に専用テストが無い | 検討(カバレッジ追加) |
| 8 | Bean Validationテストのセットアップ重複(7採用時) | 現状は据え置き |
| 9 | `PromptSanitizerTest`と`PostTest`のテスト内容重複 | コメントで意図を明記するのみ |
