output "app_url" {
  description = "Public URL of the application."
  value       = "${local.https_enabled ? "https" : "http"}://${aws_lb.main.dns_name}"
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_names" {
  description = "ECS service names (used by CD to wait for a stable deployment)."
  value       = [aws_ecs_service.claims_service.name, aws_ecs_service.fraud_service.name, aws_ecs_service.claims_ui.name]
}

output "ecr_repository_urls" {
  description = "ECR repository URL per service."
  value       = { for k, r in aws_ecr_repository.app : k => r.repository_url }
}

output "github_deploy_role_arn" {
  description = "Set this as the AWS_DEPLOY_ROLE_ARN repository variable in GitHub."
  value       = aws_iam_role.github_deploy.arn
}

output "claim_events_queue_url" {
  description = "SQS queue receiving ClaimStatusChanged events."
  value       = aws_sqs_queue.claim_events.url
}
