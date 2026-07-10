# ドキュメント索引

Tsumoryの企画・仕様関連ドキュメントの索引。各ファイルの役割と概要のみを示す(内容はここに集約しない)。

## [mymanifesto.md](./mymanifesto.md)

サービスのマニフェスト。コンセプト・ユーザー体験・AIのトーンの3点を簡潔にまとめたもの。以降のすべてのドキュメントの拠り所となる、最上位の指針。

## [phases.md](./phases.md)

開発フェーズの切り分け。マニフェストのコンセプトに照らして、フェーズ1(MVP・コアループの最短実装)とフェーズ2(体験を厚くする拡張)に分け、何をMVPに入れ何を後回しにするかの判断基準を示す。

## [phase1-spec.md](./phase1-spec.md)

フェーズ1の実装仕様。データモデル(ER図含む)・認証方針・画面一覧・エンドポイント一覧・各機能の振る舞いと制約・AI連携・画面設計・パッケージ配置・スコープ外を、実装に着手できる粒度まで詳細化したもの。

## [phase2-spec.md](./phase2-spec.md)

フェーズ2の実装仕様。フェーズ1からの差分(つぶやきのカテゴリ自動分類・過去の日記一覧・日記の検索・遡り生成)を、実装に着手できる粒度まで詳細化したもの。

## [code-review-service.md](./code-review-service.md)

service層コードのレビュー結果。可読性・保守性/エラーハンドリング/セキュリティの観点で指摘した問題点と良い点をまとめたもの。

## [code-review-non-service.md](./code-review-non-service.md)

service層以外(config/controller/domain/form/repository/security、およびリソース類)のコードレビュー結果。code-review-service.mdと同じ観点でまとめたもの。

## [code-review-resolutions.md](./code-review-resolutions.md)

code-review-service.md/code-review-non-service.mdで挙げた指摘のうち対応済みのものについて、何を・なぜ・どう直したか・どう検証したかを記録した解消メモ。
