# AWS Budget Guardrail Runbook

We use this pattern as a cost-control backstop for our small App Runner-based AWS workload.

We reduce surprise spend by combining:

- AWS Budgets cost alerts.
- Amazon SNS budget notifications.
- A Lambda function that pauses selected App Runner services.
- An AWS Budgets IAM freeze action for selected IAM entities.

We still keep normal billing reviews, tagging, log retention, and infrastructure cleanup. AWS Budgets can lag actual usage, so this is a guardrail, not a hard billing circuit breaker.

## Flow

```text
AWS Budget threshold exceeded
  -> Email notification
  -> SNS notification
      -> Lambda function
          -> Pause selected App Runner services
  -> AWS Budgets action
      -> Attach IAM freeze policy to selected IAM users, groups, or roles
```

## Budget

We use a monthly cost budget with two actual-cost thresholds.

| Threshold | Action |
| --- | --- |
| 50% actual cost | Send warning email |
| 100% actual cost | Send alert email, publish to SNS, invoke Lambda, pause configured App Runner services, attach freeze policy |

We set the budget amount per environment.

## SNS Topic

We use a standard SNS topic for budget automation.

Placeholder:

```text
<budget-automation-topic-name>
```

We set the topic policy so AWS Budgets can publish messages.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowBudgetsToPublish",
      "Effect": "Allow",
      "Principal": {
        "Service": "budgets.amazonaws.com"
      },
      "Action": "SNS:Publish",
      "Resource": "arn:aws:sns:<region>:<account-id>:<topic-name>",
      "Condition": {
        "StringEquals": {
          "aws:SourceAccount": "<account-id>"
        },
        "ArnLike": {
          "aws:SourceArn": "arn:aws:budgets::<account-id>:*"
        }
      }
    }
  ]
}
```

## Lambda

We subscribe a Python Lambda function to the SNS topic.

We configure the App Runner service list through:

```text
APP_RUNNER_SERVICE_ARNS=<comma-separated-app-runner-service-arns>
```

We only include App Runner services that are safe to pause when the guardrail triggers.

| Service | Environment | Paused by Budget |
| --- | --- | --- |
| Backend API | dev | Yes |
| Frontend app | dev | Yes |
| Backend API | prod | No, for now |
| Frontend app | prod | No, for now |

### Lambda Code

```python
import os

import boto3


apprunner = boto3.client("apprunner")

SKIP_STATUSES = {
    "PAUSED",
    "OPERATION_IN_PROGRESS",
    "DELETED",
    "CREATE_FAILED",
    "DELETE_FAILED",
}


def lambda_handler(event, context):
    service_arns_env = os.environ.get("APP_RUNNER_SERVICE_ARNS", "")
    service_arns = [
        arn.strip()
        for arn in service_arns_env.split(",")
        if arn.strip()
    ]

    if not service_arns:
        result = {
            "action": "failed",
            "error": "APP_RUNNER_SERVICE_ARNS is not configured",
        }
        print(result)
        return {"results": [result]}

    results = []
    for service_arn in service_arns:
        try:
            service = apprunner.describe_service(ServiceArn=service_arn)["Service"]
            status = service["Status"]

            if status in SKIP_STATUSES:
                results.append({
                    "serviceArn": service_arn,
                    "status": status,
                    "action": "skipped",
                })
                continue

            response = apprunner.pause_service(ServiceArn=service_arn)
            results.append({
                "serviceArn": service_arn,
                "status": response["Service"]["Status"],
                "action": "pause_requested",
            })
        except Exception as exc:
            results.append({
                "serviceArn": service_arn,
                "action": "failed",
                "error": str(exc),
            })

    print(results)
    return {"results": results}
```

## Lambda Permissions

We give the Lambda execution role normal CloudWatch logging permissions and permission to describe and pause the selected App Runner services.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PauseSpecificAppRunnerServices",
      "Effect": "Allow",
      "Action": [
        "apprunner:DescribeService",
        "apprunner:PauseService"
      ],
      "Resource": [
        "arn:aws:apprunner:<region>:<account-id>:service/<service-name>/<service-id>"
      ]
    }
  ]
}
```

For multiple App Runner services, we list each service ARN explicitly.

We keep the SNS topic subscribed to the Lambda. The Lambda resource policy allows SNS to invoke the function.

## Budget To SNS Wiring

We publish the 100% actual-cost budget alert to the SNS topic.

```text
Budget alert
  Threshold: actual cost > 100%
  Email: enabled
  SNS topic: enabled
  SNS topic ARN: arn:aws:sns:<region>:<account-id>:<topic-name>
```

## IAM Freeze Action

We use an AWS Budgets action to attach a freeze policy to selected IAM entities.

We use the freeze policy to stop additional deployments, resource creation, and manual cost-growing changes after the budget threshold is exceeded. It still allows read-only inspection and recovery actions.

Valid target types for this pattern:

- IAM user.
- IAM group.
- IAM role.

We do not target AWS service-linked roles.

Budget action execution role permission:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowBudgetFreezePolicyOnUsers",
      "Effect": "Allow",
      "Action": [
        "iam:AttachUserPolicy",
        "iam:DetachUserPolicy"
      ],
      "Resource": [
        "arn:aws:iam::<account-id>:user/<user-name>"
      ],
      "Condition": {
        "ArnEquals": {
          "iam:PolicyARN": "arn:aws:iam::<account-id>:policy/<freeze-policy-name>"
        }
      }
    },
    {
      "Sid": "AllowBudgetFreezePolicyOnGroups",
      "Effect": "Allow",
      "Action": [
        "iam:AttachGroupPolicy",
        "iam:DetachGroupPolicy"
      ],
      "Resource": [
        "arn:aws:iam::<account-id>:group/<group-name>"
      ],
      "Condition": {
        "ArnEquals": {
          "iam:PolicyARN": "arn:aws:iam::<account-id>:policy/<freeze-policy-name>"
        }
      }
    },
    {
      "Sid": "AllowBudgetFreezePolicyOnRoles",
      "Effect": "Allow",
      "Action": [
        "iam:AttachRolePolicy",
        "iam:DetachRolePolicy"
      ],
      "Resource": [
        "arn:aws:iam::<account-id>:role/<role-name>"
      ],
      "Condition": {
        "ArnEquals": {
          "iam:PolicyARN": "arn:aws:iam::<account-id>:policy/<freeze-policy-name>"
        }
      }
    }
  ]
}
```

We only keep the target sections we actually use.

## App Runner Pause Behavior

When App Runner pauses a service:

- Compute capacity goes to zero.
- The service stops serving traffic.
- Ephemeral runtime state is not preserved.
- The service can be resumed manually.
- The currently deployed version is reused when resumed.
- Newer images are not automatically deployed just because they were pushed while the service was paused.

Resume path:

```text
AWS Console
  -> App Runner
  -> Services
  -> Select service
  -> Actions
  -> Resume
```

## Safe Test Process

We test this guardrail with dev-only App Runner service ARNs first.

1. Configure `APP_RUNNER_SERVICE_ARNS` with dev-only service ARNs.
2. Publish a test message to the SNS topic.
3. Check Lambda logs in CloudWatch.
4. Confirm the dev App Runner services move to `PAUSED`.
5. Resume the dev App Runner services.
6. Restore `APP_RUNNER_SERVICE_ARNS` to the intended service list.

## PrivateLink Cost Note

Our prod backend uses Neon PrivateLink. Dev App Runner services use public egress and non-private Neon connections.

Interface VPC endpoints create baseline monthly cost because AWS bills endpoint/AZ-hours plus data processing.

Current endpoint shape:

```text
3 interface endpoints x 3 subnets/AZs = 9 endpoint ENIs
3 interface endpoints x 1 subnet/AZ  = 3 endpoint ENIs
```

We currently use one subnet/AZ per Neon endpoint to reduce cost. That lowers multi-AZ resiliency. See `private-app-runner-neon.md` for the current Neon endpoint layout and the steps to add the other subnets back.

Gateway endpoints, such as DynamoDB or S3 gateway endpoints, do not create endpoint network interfaces and are not the same hourly PrivateLink-style cost driver. We keep the DynamoDB gateway endpoint.

## Operational Runbook

When the budget threshold is exceeded:

1. AWS Budgets sends an email alert.
2. AWS Budgets publishes to SNS.
3. SNS invokes Lambda.
4. Lambda pauses configured App Runner services.
5. AWS Budgets attaches the freeze policy to selected IAM entities.

To resume services:

1. Go to App Runner.
2. Select each paused service.
3. Choose `Actions`.
4. Choose `Resume`.

To remove the IAM freeze policy manually:

1. Go to IAM.
2. Open the target user, group, or role.
3. Open permissions.
4. Remove the freeze policy.

## Verification Checklist

- [ ] Budget monthly amount is correct.
- [ ] Warning alert is configured.
- [ ] Freeze alert is configured.
- [ ] Email notification is configured.
- [ ] SNS topic is configured on the freeze alert.
- [ ] SNS topic policy allows `budgets.amazonaws.com` to publish.
- [ ] SNS topic has a confirmed Lambda subscription.
- [ ] Lambda has `APP_RUNNER_SERVICE_ARNS` configured.
- [ ] Lambda execution role allows `apprunner:DescribeService`.
- [ ] Lambda execution role allows `apprunner:PauseService`.
- [ ] Lambda code is deployed.
- [ ] Lambda logs are visible in CloudWatch.
- [ ] IAM freeze action targets only intended IAM entities.
- [ ] Budget action execution role can attach and detach the freeze policy.
- [ ] PrivateLink endpoint subnet count is intentional.
- [ ] App Runner resume process is documented.

## Sanitization Rules

We do not commit real identifiers to public documentation.

Avoid publishing:

- AWS account IDs.
- Full ARNs.
- IAM user names.
- IAM role names.
- IAM group names.
- VPC IDs.
- Subnet IDs.
- Endpoint IDs.
- App Runner service names.
- Lambda function names that reveal project details.
- SNS topic names that reveal project details.
- Budget names that reveal internal naming.

Use placeholders instead:

```text
<account-id>
<region>
<topic-name>
<function-name>
<role-name>
<service-name>
<service-id>
<vpc-id>
<subnet-id>
<endpoint-id>
<freeze-policy-name>
```

## References

- AWS Budgets SNS notification policy: <https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-sns-policy.html>
- AWS Budgets actions: <https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/budgets-controls.html>
- App Runner pause/resume behavior: <https://docs.aws.amazon.com/apprunner/latest/dg/manage-pause.html>
- App Runner `PauseService` API: <https://docs.aws.amazon.com/apprunner/latest/api/API_PauseService.html>
- AWS PrivateLink pricing: <https://aws.amazon.com/privatelink/pricing/>
