# ECS on Fargate: no servers to patch. Services find each other through ECS Service Connect
# (http://fraud-service:8000, http://claims-service:8080), the same names docker-compose uses.

resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enhanced"
  }
}

resource "aws_service_discovery_http_namespace" "main" {
  name = "${local.name}.internal"
}

resource "aws_ecr_repository" "app" {
  for_each             = toset(["claims-service", "fraud-service", "claims-ui"])
  name                 = "${var.project}/${each.key}"
  image_tag_mutability = "IMMUTABLE" # a SHA tag always means the same image

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 30 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 30 }
      action       = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "app" {
  for_each          = aws_ecr_repository.app
  name              = "/ecs/${local.name}/${each.key}"
  retention_in_days = 90
}

# ---------------------------------------------------------------------------- IAM
# Execution role: what ECS needs to start tasks (pull images, write logs, read the task's secrets).
# Task roles: what the application code itself may call, per service.

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${local.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "read_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = local.readable_secret_arns
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-app-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.read_secrets.json
}

resource "aws_iam_role" "claims_task" {
  name               = "${local.name}-claims-service"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "claims_task" {
  statement {
    sid       = "PublishClaimEvents"
    actions   = ["sqs:SendMessage", "sqs:GetQueueUrl"]
    resources = [aws_sqs_queue.claim_events.arn]
  }
}

resource "aws_iam_role_policy" "claims_task" {
  name   = "claims-service"
  role   = aws_iam_role.claims_task.id
  policy = data.aws_iam_policy_document.claims_task.json
}

resource "aws_iam_role" "no_permissions_task" {
  name               = "${local.name}-no-aws-access"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

# ---------------------------------------------------------------------------- task definitions

locals {
  image = { for k, r in aws_ecr_repository.app : k => "${r.repository_url}:${var.image_tag}" }

  log_config = { for k, g in aws_cloudwatch_log_group.app : k => {
    logDriver = "awslogs"
    options = {
      awslogs-group         = g.name
      awslogs-region        = var.aws_region
      awslogs-stream-prefix = k
    }
  } }

  secret = { for k, s in aws_secretsmanager_secret.app : k => s.arn }
}

resource "aws_ecs_task_definition" "claims_service" {
  family                   = "${local.name}-claims-service"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.claims_task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "claims-service"
    image        = local.image["claims-service"]
    essential    = true
    portMappings = [{ name = "claims-http", containerPort = 8080, protocol = "tcp", appProtocol = "http" }]
    environment = [
      { name = "DB_URL", value = "jdbc:postgresql://${aws_rds_cluster.claims.endpoint}:5432/claims?sslmode=require" },
      { name = "FRAUD_SERVICE_URL", value = "http://fraud-service:8000" },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "AWS_SQS_ENDPOINT", value = "" }, # real SQS
      { name = "CLAIM_EVENTS_QUEUE_NAME", value = aws_sqs_queue.claim_events.name },
      { name = "SPRING_PROFILES_ACTIVE", value = "json-logs" },
    ]
    secrets = [
      { name = "DB_USERNAME", valueFrom = "${local.aurora_secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${local.aurora_secret_arn}:password::" },
      { name = "JWT_SECRET", valueFrom = local.secret["jwt-secret"] },
      { name = "FRAUD_API_KEY", valueFrom = local.secret["fraud-api-key"] },
      { name = "DEMO_SUBMITTER_PASSWORD", valueFrom = local.secret["demo-submitter"] },
      { name = "DEMO_ADJUDICATOR_PASSWORD", valueFrom = local.secret["demo-adjudicator"] },
    ]
    healthCheck = {
      command     = ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health/liveness || exit 1"]
      interval    = 15
      timeout     = 5
      retries     = 3
      startPeriod = 90
    }
    logConfiguration = local.log_config["claims-service"]
  }])
}

resource "aws_ecs_task_definition" "fraud_service" {
  family                   = "${local.name}-fraud-service"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.no_permissions_task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "fraud-service"
    image        = local.image["fraud-service"]
    essential    = true
    portMappings = [{ name = "fraud-http", containerPort = 8000, protocol = "tcp", appProtocol = "http" }]
    environment  = [{ name = "FRAUD_LOG_FORMAT", value = "json" }]
    secrets = [
      { name = "FRAUD_MONGO_URI", valueFrom = aws_secretsmanager_secret.fraud_mongo_uri.arn },
      { name = "FRAUD_API_KEY", valueFrom = local.secret["fraud-api-key"] },
      { name = "FRAUD_MEMBER_HASH_KEY", valueFrom = local.secret["member-hash-key"] },
    ]
    healthCheck = {
      command     = ["CMD", "python", "-c", "import sys, urllib.request; sys.exit(0 if urllib.request.urlopen('http://localhost:8000/health/live', timeout=2).status == 200 else 1)"]
      interval    = 15
      timeout     = 5
      retries     = 3
      startPeriod = 20
    }
    logConfiguration = local.log_config["fraud-service"]
  }])
}

resource "aws_ecs_task_definition" "claims_ui" {
  family                   = "${local.name}-claims-ui"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.no_permissions_task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "claims-ui"
    image        = local.image["claims-ui"]
    essential    = true
    portMappings = [{ name = "ui-http", containerPort = 8080, protocol = "tcp", appProtocol = "http" }]
    environment  = [{ name = "CLAIMS_API_URL", value = "http://claims-service:8080" }]
    healthCheck = {
      command     = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1"]
      interval    = 15
      timeout     = 5
      retries     = 3
      startPeriod = 10
    }
    logConfiguration = local.log_config["claims-ui"]
  }])
}

# ---------------------------------------------------------------------------- services
# Rolling deployments with the circuit breaker: a release whose tasks can't become healthy rolls back on its own.

resource "aws_ecs_service" "fraud_service" {
  name            = "fraud-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.fraud_service.arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = module.vpc.private_subnets
    security_groups = [aws_security_group.apps.id]
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn
    service {
      port_name = "fraud-http"
      client_alias {
        dns_name = "fraud-service"
        port     = 8000
      }
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  propagate_tags = "SERVICE"
}

resource "aws_ecs_service" "claims_service" {
  name                              = "claims-service"
  cluster                           = aws_ecs_cluster.main.id
  task_definition                   = aws_ecs_task_definition.claims_service.arn
  desired_count                     = var.claims_service_count
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 120

  network_configuration {
    subnets         = module.vpc.private_subnets
    security_groups = [aws_security_group.apps.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.claims_service.arn
    container_name   = "claims-service"
    container_port   = 8080
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn
    service {
      port_name = "claims-http"
      client_alias {
        dns_name = "claims-service"
        port     = 8080
      }
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  propagate_tags = "SERVICE"

  lifecycle {
    ignore_changes = [desired_count] # owned by autoscaling after creation
  }

  depends_on = [aws_lb_listener.http]
}

resource "aws_ecs_service" "claims_ui" {
  name            = "claims-ui"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.claims_ui.arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = module.vpc.private_subnets
    security_groups = [aws_security_group.apps.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.claims_ui.arn
    container_name   = "claims-ui"
    container_port   = 8080
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  propagate_tags = "SERVICE"

  depends_on = [aws_lb_listener.http]
}

# Scale claims-service on CPU; it's the service that does the real work per request.
resource "aws_appautoscaling_target" "claims_service" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.claims_service.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.claims_service_count
  max_capacity       = var.claims_service_count * 4
}

resource "aws_appautoscaling_policy" "claims_service_cpu" {
  name               = "${local.name}-claims-cpu"
  service_namespace  = aws_appautoscaling_target.claims_service.service_namespace
  resource_id        = aws_appautoscaling_target.claims_service.resource_id
  scalable_dimension = aws_appautoscaling_target.claims_service.scalable_dimension
  policy_type        = "TargetTrackingScaling"

  target_tracking_scaling_policy_configuration {
    target_value       = 60
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
