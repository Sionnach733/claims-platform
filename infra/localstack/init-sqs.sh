#!/bin/bash
# Runs inside LocalStack once it is ready: create the claim-status event queue + DLQ.
set -euo pipefail

awslocal sqs create-queue --queue-name claim-status-events-dlq
DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/claim-status-events-dlq \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

awslocal sqs create-queue --queue-name claim-status-events \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}"

echo "SQS queues created"
