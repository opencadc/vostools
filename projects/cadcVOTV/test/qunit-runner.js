//
// Runs a folder of tests or a single test, using QUnit and outputs a JUnit xml file ready for sweet integration with your CI server.
//
// @usage
// DISPLAY=:0 phantomjs qunit-runner.js --qunit qunit.js --tests tests-foler/ --package package.json --junit qunit-results.xml
//
// package.json is a common JS package file which contains a list of files that need to be loaded before the tests are run. In this instance
// the package.json lists the files for a JS SDK, that gets loaded and then the tests test the SDK.
//
// Designed to run via PhantomJS.
//

//
// Runs a folder of tests or a single test, using QUnit and outputs a JUnit xml file ready for sweet integration with your CI server.
//
// Designed to run via PhantomJS.
//
//
// Runs a folder of tests or a single test, using QUnit and outputs a JUnit xml file ready for sweet integration with your CI server.
//
// Designed to run via PhantomJS.
//

var qunitRunner;

var QUnitRunner = function (args) {
	// Take the args array and turn it into an object has using the passed keys.
	// e.g. --hello world becomes console.log(options.hello); prints "World".

	var opts = {},
		numArgs = args.length;
	for (var i = 0; i < numArgs; i++) {
		if (args[i].indexOf("--") === 0) { // Is this a new key.
			// Yes it is.
			if ((i + 1) < numArgs && args[i + 1].indexOf("--") === 0) { // Is the next arg a key as well?
				// Yes it is, in which case set this key as true.
				opts[args[i].replace("--", "")] = true;
			} else if ((i + 1) < numArgs) { // Ok no it's not.
				// Thus set this key with the next arg as the value
				opts[args[i].replace("--", "")] = args[i + 1];
			}
		}
	}

	this.options = opts;

	// Now let's get a file system handle.
	this.fs = require("fs");
  qunitRunner = this;
};

QUnitRunner.prototype = {
	verify: function () {
		var ok = false;

		if (typeof this.options.qunit == "undefined") {
			throw new Error("You need to specify where qunit.js lives, like: `--qunit qunit.js`.");
		} else if (!this.fs.isFile(this.options.qunit) || !this.fs.isReadable(this.options.qunit)) {
			throw new Error("Cannot find qunit.js");
		} else {
			ok = true;
		}

		if (typeof this.options.tests == "undefined") {
			throw new Error("You need to specify where your tests live, like: `--tests mytests.js`.");
		} else if (this.options.tests.indexOf(".js") !== -1 && !this.fs.isFile(this.options.tests)) { // Validate file exists if it has .js at the end.
			throw new Error("Test file '"+this.options.tests+"' cannot be found.");
		} else if (!this.fs.isFile(this.options.tests) && !this.fs.isDirectory(this.options.tests)) {
			throw new Error("Cannot find test directory '"+this.options.tests+"'.");
		} else if (!this.fs.isReadable(this.options.tests)) {
			throw new Error("Cannot read test file or directory '"+this.options.tests+"'.");
		} else {
			ok = true; // tests is there and is good to go.
		}

		return ok;
	},
	//
	// Does the actualy running of the tests (either a test file or a folder of tests).
	//
	// @source http://whileonefork.blogspot.com/2011/07/javascript-unit-tests-with-qunit-ant.html
	// @source https://gist.github.com/1363104
	//
	startQunit: function ()
  {
		var self = this,
			testsPassed = 0,
			testsFailed = 0,
			module, moduleStart, testStart, testCases = [],
			current_test_assertions = [],
			junitxml = '<?xml version="1.0" encoding="UTF-8"?>\n<testsuites name="testsuites">\n';;

		if (typeof this.options.junit != "undefined")
    {
			console.log("Going to produce JUnit xml file: "+this.options.junit);
		}

		QUnit.begin({});

		function extend(a, b) {
			for ( var prop in b ) {
				if ( b[prop] === undefined ) {
					delete a[prop];
				} else {
					a[prop] = b[prop];
				}
			}

			return a;
		}

		// Initialize the config, saving the execution queue
		var oldconfig = extend({}, QUnit.config);
		QUnit.init();
		extend(QUnit.config, oldconfig);

		QUnit.testStart = function() {
			testStart = new Date();
		};

		QUnit.moduleStart = function(context) {
			moduleStart = new Date();
			module = context.name;
			testCases = [];
		};

		QUnit.moduleDone = function(context) {
			// context = { name, failed, passed, total }
			var xml = '\t<testsuite name="' + context.name + '" errors="0" failures="' + context.failed + '" tests="' + context.total + '" time="' + (new Date() - moduleStart) / 1000 + '"';
			if (testCases.length) {
				xml += '>\n';
				for (var i = 0, l = testCases.length; i < l; i++) {
					xml += testCases[i];
				}
				xml += '\t</testsuite>';
			} else {
				xml += '/>\n';
			}

			junitxml += xml;
		};

		QUnit.testDone = function(result) {
			if (0 === result.failed) {
				testsPassed++;
			} else {
				testsFailed++;
			}

			console.log((0 === result.failed ? '\033[1;92mPASS\033[0m' : '\033[1;31mFAIL\033[0m') + ' - ' + result.name + ' completed: ');

			// result = { name, failed, passed, total }
			var xml = '\t\t<testcase classname="' + module + '" name="' + result.name + '" time="' + (new Date() - testStart) / 1000 + '"';
			if (result.failed) {
				xml += '>\n';
				for (var i = 0; i < current_test_assertions.length; i++) {
					xml += "\t\t\t" + current_test_assertions[i];
				}
				xml += '\t\t</testcase>\n';
			} else {
				xml += '/>\n';
			}
			current_test_assertions = [];

			testCases.push(xml);
		};

		var running = true;
		QUnit.done = function(i) {
			console.log(testsPassed + ' of ' + (testsPassed + testsFailed) + ' tests successful.');
			console.log('TEST RUN COMPLETED: ' + (0 === testsFailed ? '\033[1;92mWIN\033[0m' : '\033[1;31mFAIL\033[0m'));
			running = false;

			if (typeof self.options.junit != "undefined") {
				junitxml += '</testsuites>';

				// Ok now let's write that xml file to where we told it to. (well actually the user did via the --junit option).
				if (!self.fs.isFile(self.options.junit)) {
					self.fs.write(self.options.junit, junitxml, "w");
				} else {
					console.log("Cannot write junit results file.");
				}
			}
		};

		QUnit.log = function(details) {
			//details = { result , actual, expected, message }
			if (details.result) {
				return;
			}
			var message = details.message || "";
			if (details.expected) {
				if (message) {
					message += ", ";
				}
				message = "expected: " + details.expected + ", but was: " + details.actual;
			}
			var xml = '<failure type="failed" message="' + details.message.replace(/ - \{((.|\n)*)\}/, "") + '"/>\n';

			current_test_assertions.push(xml);
		};

		//Instead of QUnit.start(); just directly exec; the timer stuff seems to invariably screw us up and we don't need it
		QUnit.config.semaphore = 0;
		while( QUnit.config.queue.length ) {
			QUnit.config.queue.shift()();
		}

		// wait for completion
		var ct = 0;
		while ( running ) {
			if (ct++ % 1000000 == 0) {
				console.log('Queue is at ' + QUnit.config.queue.length);
			}

			if (!QUnit.config.queue.length) {
				QUnit.done();
			}
		}

		//exit code is # of failed tests; this facilitates Ant failonerror. Alternately, 1 if testsFailed > 0.
		phantom.exit(testsFailed);
	},
	run: function () {
		//
		// Loads the test file or folder of tests files.
		//
		var loadTests = function () {
			console.log("Load those tests");
			if (qunitRunner.fs.isFile(qunitRunner.options.tests))
      {
				console.log("Load test file: "+qunitRunner.options.tests);
				phantom.injectJs(qunitRunner.options.tests);

				// Now run the tests.
				qunitRunner.startQunit();
			} else if (qunitRunner.fs.isDirectory(qunitRunner.options.tests)) {
				var data = qunitRunner.fs.list(qunitRunner.options.tests);

				for (var i = 0; i < data.length; i++)
        {
					if (data[i].indexOf(".js") !== -1)
          {
						console.log("Load test file: "+qunitRunner.options.tests+qunitRunner.fs.separator+data[i]);
						phantom.injectJs(qunitRunner.options.tests+qunitRunner.fs.separator+data[i]);
					}
				}

				// Now run the tests.
        qunitRunner.startQunit();
			} else {
				throw new Error("Tests is not a file or a directory?! I don't know what to do with that.");
			}
		};

		//
		// Iterates the required scripts detailed in a CommonJS package file, loading them before testing.
		//
		var requirements = function (pkg)
    {
      var reqts;

      if (typeof pkg == "string")
      {
        reqts = JSON.parse(pkg);
      }
      else if (typeof pkg == "object")
      {
        reqts = pkg;
      }
      else
      {
        console.error("Oopsie, package option should be a CommonJS package.json file and should have the option scripts, so we can load those scripts.");
        throw new Error("Oopsie, package option should be a CommonJS package.json file and should have the option scripts, so we can load those scripts.");
      }

      var parts = qunitRunner.options.package.split(qunitRunner.fs.separator);
      parts.pop();
      var path = parts.join(qunitRunner.fs.separator)+qunitRunner.fs.separator;

      // Now interate those scripts injecting each one into the current conext.
      for (var key in reqts.scripts)
      {
        if (reqts.scripts.hasOwnProperty(key))
        {
          console.log("Injecting: "+path+reqts.scripts[key]);

          try
          {
            phantom.injectJs(path+reqts.scripts[key]);
          }
          catch (e)
          {
            console.error("Oh dear.\n" + e);
          }
        }
      }

      // All scripts loaded, execute the tests.
      loadTests();
		};

		// First let's verify those options.
		if (qunitRunner.verify())
    {
			console.log("Verified passed options.");

			console.log("Loading QUnit...");
			phantom.injectJs(this.options.qunit);
			console.log("QUnit loaded.");

			if (typeof this.options.package != "undefined") {
				console.log("Found CommonJS package file let's see if we can load it.");

				if (this.fs.isReadable(this.options.package)) {
					console.log("Loading: "+this.options.package);

          requirements(this.fs.read(this.options.package));

//					var package = "requirements.call(this, "+this.fs.read(this.options.package)+")";

					// Now eval that beast.
//					eval(package);
				} else {
					throw new Error("Cannot read package file.");
				}
			}
			else
			{
				console.log("No package file preload, just run those tests...");
				loadTests.call(this);
			}
		}
	}
};

var runner = new QUnitRunner(phantom.args);

runner.run();