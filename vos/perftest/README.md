# Performance Tests

Testing was done using a product called [Locust](https://locust.io).  It runs Python testing code exercising the transfer negotiation and vcp. 

Locust allows us to scale up Workers (Called Users to Locust) to issue multiple simultaneous requests.  Tests are executed through an intuitive UI, which features the ability to ramp up Workers per second to a set maximum.  Tests run until they are manually stopped.

There are 2 types of tests: 
  * `global` tests the performance of transfer negotiation on the global site. No bytes are exchanged. 
  * `site` tests the performance of a site. No transfer negotiantion is required. Just A&A and bytes transfer.
## Running it

The system runs on port 8089 with Docker and requires a volume to be created and populated first with:

 * `cadcproxy.pem` file of the authenticated user that runs the tests.
 * `test_config` file of the authenticated user that runs the tests.

### Docker Run

On one of the `global` or `site` directories, the image will need to be built first:

`$ docker build -t [newstorage_site_test|newstorage_global_test] .`

Foreground run:
`$ docker run -t --rm -p 8089:8089 [newstorage_site_test|newstorage_global_test]`

Background run:
`$ docker run -t -d --name locust -p 8089:8089 [newstorage_site_test|newstorage_global_test]`

Then you can visit http://localhost:8089 to use the UI and start a run.

Both tests can be run at the same time on the same machine if different ports on the host are used.

### Running on distributed system

`global` tests are not CPU or network intensive and can be run (swarmed) from the same host. `site` tests on the other
hand transfer bytes between the service and host and could be run on a distributed system. Tests are easier to run
with `docker-compose` commands using the `docker-compose.yml` provided for each type of test.

#### Multiple workers from the same host

To start the system with 4 workers, run the command:
`$docker-compose up --scale worker=4`

Then use the web page at `http://0.0.0.0:8089/` to run the tests and collect data.

To shut down the system run:
`docker-compose down`

#### Multiple workers on different hosts

Each host needs to have a config file as above.

Start master node:
`$docker-compose run master --expected-workers=X`

Then on each node start the workers:
`$docker-compose run worker --master-host=X.X.X.X --master-port=5557`

Number of attached workers will be see at the `http://0.0.0.0:8089/`. Tests cannot be started untill all the workers have
attached.

Distributed tests can also be run on Kubernetes. Please consult the `locust` documentation
`https://docs.locust.io/en/stable/running-locust-docker.html#running-locust-docker) to find out the procedure as well
as other configurations and modes that can be set up.





IMPORTANT: Make sure that the testing machines are accessible (they are either public or the appropriate VPN is running)
