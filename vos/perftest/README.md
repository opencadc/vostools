# Performance Tests

Testing was done using a product called [Locust](https://locust.io).  It runs Python testing code exercising the transfer negotiation and vcp. 

Locust allows us to scale up Workers (Called Users to Locust) to issue multiple simultaneous requests.  Tests are executed through an intuitive UI, which features the ability to ramp up Workers per second to a set maximum.  Tests run until they are manually stopped.

The test_config file contains configuration info used to execute the tests tests.

## Running it

The system runs on port 8089 with Docker and requires two volumes to be created and populated first:

 * `cert`: to hold the `cadcproxy.pem` file of the authenticated user that runs the tests.


### Docker Run

The image will need to be built first:

`$ docker build -t myimage .`

Foreground run:
`$ docker run -t --rm -p 8089:8089 myimage`

Background run:
`$ docker run -t -d --name locust -p 8089:8089 myimage`

Then you can visit http://localhost:8089 to use the UI and start a run.

IMPORTANT: Make sure that the testing machines are accessible (they are either public or the appropriate VPN is running)
