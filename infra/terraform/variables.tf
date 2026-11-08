variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "eu-west-1"
}

variable "project" {
  description = "Project name, used as a prefix for resource names."
  type        = string
  default     = "claims-platform"
}

variable "environment" {
  description = "Environment name (e.g. staging, production)."
  type        = string
  default     = "production"
}

variable "image_tag" {
  description = "Container image tag to deploy (the git commit SHA, set by the CD pipeline)."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository allowed to deploy, as owner/name."
  type        = string
}

variable "create_github_oidc_provider" {
  description = "Create the GitHub OIDC identity provider. Set false if the account already has one."
  type        = bool
  default     = true
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.40.0.0/16"
}

variable "certificate_arn" {
  description = "ACM certificate ARN for HTTPS on the load balancer. Empty = HTTP only (demo)."
  type        = string
  default     = ""
}

variable "aurora_engine_version" {
  description = "Aurora PostgreSQL engine version."
  type        = string
  default     = "16.6"
}

variable "aurora_min_acu" {
  description = "Aurora Serverless v2 minimum capacity (ACUs)."
  type        = number
  default     = 0.5
}

variable "aurora_max_acu" {
  description = "Aurora Serverless v2 maximum capacity (ACUs)."
  type        = number
  default     = 8
}

variable "claims_service_count" {
  description = "Baseline number of claims-service tasks (autoscaling can add more)."
  type        = number
  default     = 2
}

variable "alarm_email" {
  description = "Email address subscribed to operational alarms. Empty = no subscription."
  type        = string
  default     = ""
}
