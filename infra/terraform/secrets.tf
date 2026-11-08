# Application secrets live in Secrets Manager and reach containers as ECS "secrets" (injected at start),
# never as plain environment variables in the task definition.

resource "random_password" "app" {
  for_each = toset(["jwt-secret", "fraud-api-key", "member-hash-key", "demo-submitter", "demo-adjudicator"])
  length   = 48
  special  = false
}

resource "aws_secretsmanager_secret" "app" {
  for_each                = random_password.app
  name                    = "${local.name}/${each.key}"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "app" {
  for_each      = random_password.app
  secret_id     = aws_secretsmanager_secret.app[each.key].id
  secret_string = each.value.result
}

resource "aws_secretsmanager_secret" "fraud_mongo_uri" {
  name                    = "${local.name}/fraud-mongo-uri"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "fraud_mongo_uri" {
  secret_id = aws_secretsmanager_secret.fraud_mongo_uri.id
  secret_string = format(
    "mongodb://%s:%s@%s:27017/?tls=true&tlsCAFile=/etc/ssl/certs/rds-global-bundle.pem&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false",
    aws_docdb_cluster.fraud.master_username,
    random_password.docdb.result,
    aws_docdb_cluster.fraud.endpoint,
  )
}

locals {
  # Aurora's AWS-managed secret is JSON {"username": ..., "password": ...}; ECS can pick a key out of it.
  aurora_secret_arn = aws_rds_cluster.claims.master_user_secret[0].secret_arn

  readable_secret_arns = concat(
    [for s in aws_secretsmanager_secret.app : s.arn],
    [aws_secretsmanager_secret.fraud_mongo_uri.arn, local.aurora_secret_arn],
  )
}
