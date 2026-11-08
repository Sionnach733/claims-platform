data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name = "${var.project}-${var.environment}"
  azs  = slice(data.aws_availability_zones.available.names, 0, 2)
}

# Three subnet tiers across two AZs:
#   public   - load balancer and NAT only
#   private  - ECS tasks (no public IPs)
#   database - Aurora and DocumentDB (no route to the internet at all)
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.7"

  name = local.name
  cidr = var.vpc_cidr
  azs  = local.azs

  public_subnets   = [for i, _ in local.azs : cidrsubnet(var.vpc_cidr, 8, i)]
  private_subnets  = [for i, _ in local.azs : cidrsubnet(var.vpc_cidr, 8, i + 10)]
  database_subnets = [for i, _ in local.azs : cidrsubnet(var.vpc_cidr, 8, i + 20)]

  create_database_subnet_group = true

  enable_nat_gateway = true
  # Cost trade-off: one NAT gateway. For full AZ independence, use one per AZ.
  single_nat_gateway   = true
  enable_dns_hostnames = true
  enable_dns_support   = true

  enable_flow_log                                 = true
  create_flow_log_cloudwatch_log_group            = true
  create_flow_log_cloudwatch_iam_role             = true
  flow_log_cloudwatch_log_group_retention_in_days = 90
}

# ---------------------------------------------------------------------------- security groups
# Least privilege between tiers: internet -> ALB -> app tasks -> databases. Nothing else.

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "Public load balancer"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP (redirected to HTTPS when a certificate is configured)"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_apps" {
  security_group_id            = aws_security_group.alb.id
  description                  = "To application tasks"
  referenced_security_group_id = aws_security_group.apps.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "apps" {
  name        = "${local.name}-apps"
  description = "ECS tasks"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "apps_from_alb" {
  security_group_id            = aws_security_group.apps.id
  description                  = "UI and claims-service from the load balancer"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "apps_internal" {
  security_group_id            = aws_security_group.apps.id
  description                  = "Service-to-service (UI -> claims-service, claims-service -> fraud-service)"
  referenced_security_group_id = aws_security_group.apps.id
  from_port                    = 8000
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "apps_all" {
  security_group_id = aws_security_group.apps.id
  description       = "Outbound: databases, AWS APIs (SQS, Secrets Manager, ECR, CloudWatch) via NAT"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "databases" {
  name        = "${local.name}-databases"
  description = "Aurora PostgreSQL and DocumentDB"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "postgres_from_apps" {
  security_group_id            = aws_security_group.databases.id
  description                  = "PostgreSQL from application tasks"
  referenced_security_group_id = aws_security_group.apps.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "mongo_from_apps" {
  security_group_id            = aws_security_group.databases.id
  description                  = "DocumentDB from application tasks"
  referenced_security_group_id = aws_security_group.apps.id
  from_port                    = 27017
  to_port                      = 27017
  ip_protocol                  = "tcp"
}
