# MindBridge API cURL Reference

This README lists the backend APIs you can bind to the UI.

Base URL:

```bash
http://localhost:8080
```

Swagger:

```bash
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON:

```bash
http://localhost:8080/api-docs
```

## Auth Flow For UI

1. HR registers with `/api/auth/register` or logs in with `/api/auth/login`.
2. Employee registers with `/api/employees/register` or logs in with `/api/employees/login`.
3. Store the returned JWT token in the UI.
4. Send the token on protected APIs:

```bash
Authorization: Bearer <token>
```

Useful shell variables:

```bash
BASE_URL=http://localhost:8080
HR_TOKEN=<paste-hr-token>
EMP_TOKEN=<paste-employee-token>
COMPANY_ID=<paste-company-id>
EMPLOYEE_ID=<paste-employee-id>
```

Important current codebase note:

- `EmployeeController` has public-looking `register` and `login` endpoints.
- `SecurityConfig` currently does not `permitAll()` for `/api/employees/register` or `/api/employees/login`.
- That means employee signup/login may currently return `401` until security rules are updated.

## 1) HR Register Company

Endpoint:

```bash
POST /api/auth/register
```

cURL:

```bash
curl -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "companyName": "Acme Corp",
    "fullName": "Jane HR",
    "email": "jane@acme.com",
    "password": "securePassword123"
  }'
```

Expected response fields:

- `token`
- `email`
- `fullName`
- `role`
- `companyId`
- `inviteCode`
- `expiresIn`

UI use:

- Company onboarding
- Save `token`, `companyId`, and `inviteCode`

## 2) HR Login

Endpoint:

```bash
POST /api/auth/login
```

cURL:

```bash
curl -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane@acme.com",
    "password": "securePassword123"
  }'
```

UI use:

- HR login screen
- Save returned `token` and `companyId`

## 3) Employee Register With Invite Code

Endpoint:

```bash
POST /api/employees/register
```

cURL:

```bash
curl -X POST "$BASE_URL/api/employees/register" \
  -H "Content-Type: application/json" \
  -d '{
    "inviteCode": "MB-X7K2P9",
    "fullName": "Alice Johnson",
    "email": "alice@company.com",
    "password": "mypassword123"
  }'
```

Expected response fields:

- `token`
- `employeeId`
- `email`
- `fullName`
- `companyId`
- `expiresIn`

UI use:

- Employee signup screen
- Save `token`, `employeeId`, and `companyId`

## 4) Employee Login

Endpoint:

```bash
POST /api/employees/login
```

cURL:

```bash
curl -X POST "$BASE_URL/api/employees/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@company.com",
    "password": "mypassword123"
  }'
```

UI use:

- Employee login screen
- Save `token` and `employeeId`

## 5) Get Employee Profile

Endpoint:

```bash
GET /api/employees/me
```

Auth:

- Employee token required

cURL:

```bash
curl -X GET "$BASE_URL/api/employees/me" \
  -H "Authorization: Bearer $EMP_TOKEN"
```

UI use:

- Profile page
- Header/account panel

## 6) Get Employee Check-in History

Endpoint:

```bash
GET /api/employees/me/checkins
```

Auth:

- Employee token required

cURL:

```bash
curl -X GET "$BASE_URL/api/employees/me/checkins" \
  -H "Authorization: Bearer $EMP_TOKEN"
```

Response shape:

- `count`
- `checkins[]`
  - `id`
  - `date`
  - `energyScore`
  - `workloadTag`
  - `teamSupportScore`
  - `rawSentiment`

UI use:

- Employee history page
- Trend charts

## 7) Get Company Invite Code

Endpoint:

```bash
GET /api/employees/invite-code/{companyId}
```

Auth:

- HR token required

cURL:

```bash
curl -X GET "$BASE_URL/api/employees/invite-code/$COMPANY_ID" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Response shape:

- `inviteCode`
- `companyId`
- `message`

UI use:

- HR settings page
- Employee invite modal

## 8) Regenerate Company Invite Code

Endpoint:

```bash
POST /api/employees/invite-code/{companyId}/regenerate
```

Auth:

- HR token required

cURL:

```bash
curl -X POST "$BASE_URL/api/employees/invite-code/$COMPANY_ID/regenerate" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Response shape:

- `inviteCode`
- `message`

UI use:

- HR security/settings flow

## 9) Submit Employee Check-in

Endpoint:

```bash
POST /api/checkin
```

Auth:

- Any authenticated token is required
- In practice, call this from the employee UI with employee token

cURL:

```bash
curl -X POST "$BASE_URL/api/checkin" \
  -H "Authorization: Bearer $EMP_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": "'"$EMPLOYEE_ID"'",
    "message": "I am feeling quite tired today. Too many deadlines this week."
  }'
```

Success response:

- `status = success`
- `checkInId`
- `date`
- `message`

Already submitted today:

- `status = already_done`
- `message`

UI use:

- Daily check-in form
- Post-submit confirmation state

## 10) Get Check-in Prompt

Endpoint:

```bash
GET /api/checkin/prompt/{employeeId}
```

Auth:

- Authenticated token required

cURL:

```bash
curl -X GET "$BASE_URL/api/checkin/prompt/$EMPLOYEE_ID" \
  -H "Authorization: Bearer $EMP_TOKEN"
```

Response shape:

- `prompt`

UI use:

- Pre-fill greeting on check-in screen
- Chatbot/check-in assistant opening message

## 11) Dashboard Overview

Endpoint:

```bash
GET /api/dashboard/{companyId}/overview
```

Auth:

- HR token required

Optional query param:

- `date=YYYY-MM-DD`

cURL:

```bash
curl -X GET "$BASE_URL/api/dashboard/$COMPANY_ID/overview" \
  -H "Authorization: Bearer $HR_TOKEN"
```

With date:

```bash
curl -X GET "$BASE_URL/api/dashboard/$COMPANY_ID/overview?date=2026-06-16" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Response shape:

- `date`
- `companyId`
- `totalEmployees`
- `averageRiskScore`
- `highRiskCount`
- `checkInsToday`
- `riskDistribution`
- `trendSummary`
- `topStressors`
- `teamHealthStatus`
- `totalScored`

UI use:

- HR dashboard summary cards
- Risk distribution chart
- Trend widgets
- Stressor summary widgets

## 12) Dashboard Alerts

Endpoint:

```bash
GET /api/dashboard/{companyId}/alerts
```

Auth:

- HR token required

cURL:

```bash
curl -X GET "$BASE_URL/api/dashboard/$COMPANY_ID/alerts" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Response shape:

- `date`
- `alertCount`
- `alerts[]`
  - `employeeId`
  - `employeeName`
  - `riskLevel`
  - `riskScore`
  - `trendDirection`
  - `primaryStressors`
  - `aiReasoning`

UI use:

- HR alert list
- High-risk employee detail drawer/modal

## 13) Dashboard Wellness Pulse

Endpoint:

```bash
GET /api/dashboard/{companyId}/wellness-pulse
```

Auth:

- HR token required

cURL:

```bash
curl -X GET "$BASE_URL/api/dashboard/$COMPANY_ID/wellness-pulse" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Response shape:

- `energyLevel`
- `energyStatus`
- `supportIndex`
- `supportStatus`
- `burnoutRisk`
- `burnoutStatus`
- `checkInsToday`
- `totalEmployees`
- `checkInRate`

UI use:

- Dashboard KPI band
- Wellness pulse cards
- Daily participation widget

## 14) Employee List With Wellness Data

Endpoint:

```bash
GET /api/employees/{companyId}/list
```

Auth:

- HR token required

Optional query params:

- `search=<text>`
- `risk=LOW|MEDIUM|HIGH|CRITICAL|ALL`
- `department=<department-name>`

cURL:

```bash
curl -X GET "$BASE_URL/api/employees/$COMPANY_ID/list" \
  -H "Authorization: Bearer $HR_TOKEN"
```

With filters:

```bash
curl -X GET "$BASE_URL/api/employees/$COMPANY_ID/list?search=alice&risk=HIGH&department=Engineering" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Response shape:

- `companyId`
- `total`
- `employees[]`
  - `id`
  - `fullName`
  - `email`
  - `department`
  - `role`
  - `wellnessScore`
  - `riskLevel`
  - `riskScore`
  - `lastCheckIn`
  - `status`
  - `initials`

Notes from service logic:

- `riskLevel` can be `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`, or `NO_DATA`
- `lastCheckIn` is formatted as `Today`, `Yesterday`, `X days ago`, or `Never`
- `status` can be `Active`, `Alert`, `Needs Attention`, or `No Data`
- list is sorted by `riskScore` descending

UI use:

- HR employees table
- Search and filter bar
- Employee detail drilldown

## 15) Employee Stats

Endpoint:

```bash
GET /api/employees/{companyId}/stats
```

Auth:

- HR token required

cURL:

```bash
curl -X GET "$BASE_URL/api/employees/$COMPANY_ID/stats" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Response shape:

- `totalEmployees`
- `healthyCount`
- `atRiskCount`
- `criticalCount`
- `noDataCount`
- `healthyPercent`
- `atRiskPercent`
- `criticalPercent`

UI use:

- Employees page top summary cards
- Risk distribution counters

## 16) Employee Departments

Endpoint:

```bash
GET /api/employees/{companyId}/departments
```

Auth:

- HR token required

cURL:

```bash
curl -X GET "$BASE_URL/api/employees/$COMPANY_ID/departments" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Response shape:

- `departments[]`

UI use:

- Department filter dropdown

## 17) Trigger Burnout Score For One Employee

Endpoint:

```bash
POST /api/dashboard/score/{employeeId}
```

Auth:

- HR token required

cURL:

```bash
curl -X POST "$BASE_URL/api/dashboard/score/$EMPLOYEE_ID" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Response shape:

- `employeeId`
- `riskLevel`
- `riskScore`
- `trend`

UI use:

- Manual refresh action from HR dashboard
- Admin/test utility

## 18) Dev/Test Alert Trigger

Endpoint:

```bash
POST /api/test/trigger-alert/{employeeId}?riskLevel=HIGH
```

Notes:

- This is a test endpoint
- Keep it out of production UI

cURL:

```bash
curl -X POST "$BASE_URL/api/test/trigger-alert/$EMPLOYEE_ID?riskLevel=HIGH" \
  -H "Authorization: Bearer $HR_TOKEN"
```

UI use:

- Dev-only admin/testing tools

## Common Error Cases

401 unauthorized:

- Missing token
- Invalid token
- Expired token

400 bad request:

- Validation/business rule failure
- Invalid UUID
- Duplicate check-in on same day may return `status: already_done`

404 not found:

- Invalid company or employee id on some APIs

## Suggested UI Mapping

- HR auth pages:
  - `/api/auth/register`
  - `/api/auth/login`
- Employee auth pages:
  - `/api/employees/register`
  - `/api/employees/login`
- Employee app:
  - `/api/employees/me`
  - `/api/employees/me/checkins`
  - `/api/checkin/prompt/{employeeId}`
  - `/api/checkin`
- HR app:
  - `/api/employees/invite-code/{companyId}`
  - `/api/employees/invite-code/{companyId}/regenerate`
  - `/api/employees/{companyId}/list`
  - `/api/employees/{companyId}/stats`
  - `/api/employees/{companyId}/departments`
  - `/api/dashboard/{companyId}/overview`
  - `/api/dashboard/{companyId}/alerts`
  - `/api/dashboard/{companyId}/wellness-pulse`
  - `/api/dashboard/score/{employeeId}`

## Notes

- Server port is configured as `8080`.
- `/api/auth/**` is public.
- `/api/employees/{companyId}/list`, `/stats`, and `/departments` require `HR_MANAGER` or `ADMIN`.
- `/api/checkin/**` requires authentication.
- `/api/dashboard/**` is controller-intended for HR flows and should be called with HR token.
- Current `SecurityConfig` does not explicitly permit anonymous access to `/api/employees/register` and `/api/employees/login`.
- Swagger UI is available for manual testing.
