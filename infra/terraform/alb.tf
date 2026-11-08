# One public entry point. Path routing mirrors the local nginx proxy:
#   /api/*      -> claims-service
#   everything  -> claims-ui (static SPA)
# fraud-service and actuator endpoints are never reachable from the internet.

locals {
  https_enabled = var.certificate_arn != ""
}

resource "aws_lb" "main" {
  name                       = local.name
  load_balancer_type         = "application"
  internal                   = false
  subnets                    = module.vpc.public_subnets
  security_groups            = [aws_security_group.alb.id]
  drop_invalid_header_fields = true
  enable_deletion_protection = true
}

resource "aws_lb_target_group" "claims_ui" {
  name                 = "${local.name}-ui"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = module.vpc.vpc_id
  deregistration_delay = 15

  health_check {
    path    = "/healthz"
    matcher = "200"
  }
}

resource "aws_lb_target_group" "claims_service" {
  name                 = "${local.name}-api"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = module.vpc.vpc_id
  deregistration_delay = 30

  health_check {
    path                = "/actuator/health/readiness" # readiness: DB reachable, ready for traffic
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
  }
}

# Port 80: redirect to HTTPS when a certificate is configured, otherwise serve the app (demo mode).
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = local.https_enabled ? "redirect" : "forward"

    target_group_arn = local.https_enabled ? null : aws_lb_target_group.claims_ui.arn

    dynamic "redirect" {
      for_each = local.https_enabled ? [1] : []
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count             = local.https_enabled ? 1 : 0
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.claims_ui.arn
  }
}

resource "aws_lb_listener_rule" "api" {
  listener_arn = local.https_enabled ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.claims_service.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}
