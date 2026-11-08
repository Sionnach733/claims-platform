# ---------------------------------------------------------------------------- Aurora PostgreSQL (claims)
# Serverless v2: scales with load, no instance sizing. Two instances (writer + reader) in different AZs for
# failover. AWS generates and rotates the master password in Secrets Manager; it never appears in state or code.

resource "aws_rds_cluster" "claims" {
  cluster_identifier = "${local.name}-claims"
  engine             = "aurora-postgresql"
  engine_mode        = "provisioned"
  engine_version     = var.aurora_engine_version
  database_name      = "claims"
  master_username    = "claims_admin"

  manage_master_user_password = true

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.databases.id]

  storage_encrypted                   = true
  iam_database_authentication_enabled = true
  deletion_protection                 = true
  backup_retention_period             = 14
  preferred_backup_window             = "02:00-03:00"
  copy_tags_to_snapshot               = true
  skip_final_snapshot                 = false
  final_snapshot_identifier           = "${local.name}-claims-final"
  enabled_cloudwatch_logs_exports     = ["postgresql"]

  serverlessv2_scaling_configuration {
    min_capacity = var.aurora_min_acu
    max_capacity = var.aurora_max_acu
  }
}

resource "aws_rds_cluster_instance" "claims" {
  count                        = 2
  identifier                   = "${local.name}-claims-${count.index}"
  cluster_identifier           = aws_rds_cluster.claims.id
  engine                       = aws_rds_cluster.claims.engine
  engine_version               = aws_rds_cluster.claims.engine_version
  instance_class               = "db.serverless"
  performance_insights_enabled = true
  auto_minor_version_upgrade   = true
}

# ---------------------------------------------------------------------------- DocumentDB (fraud scores)
# MongoDB-compatible, managed, TLS enforced. fraud-service trusts the Amazon RDS CA bundle baked into its image.

resource "random_password" "docdb" {
  length  = 32
  special = false # embedded in a connection URI
}

resource "aws_docdb_subnet_group" "fraud" {
  name       = "${local.name}-fraud"
  subnet_ids = module.vpc.database_subnets
}

resource "aws_docdb_cluster" "fraud" {
  cluster_identifier              = "${local.name}-fraud"
  engine                          = "docdb"
  master_username                 = "fraud_admin"
  master_password                 = random_password.docdb.result
  db_subnet_group_name            = aws_docdb_subnet_group.fraud.name
  vpc_security_group_ids          = [aws_security_group.databases.id]
  storage_encrypted               = true
  deletion_protection             = true
  backup_retention_period         = 14
  skip_final_snapshot             = false
  final_snapshot_identifier       = "${local.name}-fraud-final"
  enabled_cloudwatch_logs_exports = ["audit"]
}

resource "aws_docdb_cluster_instance" "fraud" {
  count              = 1
  identifier         = "${local.name}-fraud-${count.index}"
  cluster_identifier = aws_docdb_cluster.fraud.id
  instance_class     = "db.t4g.medium"
}

# ---------------------------------------------------------------------------- SQS (claim status events)

resource "aws_sqs_queue" "claim_events_dlq" {
  name                      = "${local.name}-claim-status-events-dlq"
  message_retention_seconds = 1209600 # 14 days to investigate and redrive
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "claim_events" {
  name                       = "${local.name}-claim-status-events"
  message_retention_seconds  = 345600 # 4 days
  visibility_timeout_seconds = 60
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.claim_events_dlq.arn
    maxReceiveCount     = 5
  })
}
