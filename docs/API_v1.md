# Freerouting API

Freerouting API provides an easy access to auto-routing functionality through standard HTTP RESTful endpoints.

You can test the simple GET endpoints in your browser, but I highly recommend using a testing tool like Postman.

The base URL of the endpoints change when there is a new version or revision becomes available, and they always follow the https://api.freerouting.app/{{version}} pattern, where {{version}} is v1, v2 etc. The only exception is the 'dev' version which doesn't require authentication and returns mock data for testing.

Our current base URL is https://api.freerouting.app/v1, so opening https://api.freerouting.app/v1/system/status in your browser should return a valid JSON document unless there is an issue with the service.

## Authentication
Authentication is requered for some REST API endpoints accessing public data. Using authentication also increases your API rate limit.

Authentication works with personal access token which you can get by registering at https://auth.freerouting.app. Set the options.auth option to the token.

## Service Status

GET https://api.freerouting.app/system/status

GET https://api.freerouting.app/system/environment

## Sessions

POST https://api.freerouting.app/sessions/create

GET https://api.freerouting.app/sessions/list

GET https://api.freerouting.app/sessions/{{sessionId}}

## Routing Jobs

POST https://api.freerouting.app/jobs/enqueue

GET https://api.freerouting.app/jobs/list/{{sessionId}}

POST https://api.freerouting.app/jobs/{{jobId}}/settings

PUT https://api.freerouting.app/jobs/{{jobId}}/start

## Inputs and Outputs

POST https://api.freerouting.app/jobs/{{jobId}}/input

GET https://api.freerouting.app/jobs/{{jobId}}/output

## Progress Reports

GET https://api.freerouting.app/jobs/{{jobId}}