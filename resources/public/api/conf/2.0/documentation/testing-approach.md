<p>You can <a href="https://test-developer.service.hmrc.gov.uk/api-documentation/docs/sandbox/introduction">use the HMRC Developer Sandbox to test the API</a>. The Sandbox is an enhanced testing service that functions as a simulator of HMRC’s production environment.</p>
<p>The Sandbox for the RAS API does not currently support <a href="https://test-developer.service.hmrc.gov.uk/api-documentation/docs/sandbox/stateful-behaviour">stateful behaviour</a>, but you can use the payloads described in the resources to test specific scenarios.</p>
<p>For all the scenarios you will need you to create your own test user, which will be used to authorise the OAuth 2.0 token. This will not create a test user which will be returned when making a request to the endpoint. To create your own test user:</p>
   
   1\. Use the [Create Test User API](https://test-developer.service.hmrc.gov.uk/api-documentation/docs/api/service/api-platform-test-user/1.0#_create-a-test-user-which-is-an-organisation_post_accordion) and use relief-at-source as the service name
   
   2\. Then use the userId and password in the response to get an OAuth 2.0 access token