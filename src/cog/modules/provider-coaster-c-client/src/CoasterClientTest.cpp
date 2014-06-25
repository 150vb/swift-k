
#include <stdlib.h>
#include <list>

#include "CoasterLoop.h"
#include "CoasterClient.h"
#include "Job.h"
#include "Settings.h"

using std::cerr;
using std::cout;
using std::exception;
using std::list;

int main(void) {
	try {
		CoasterLoop loop;
		loop.start();

		CoasterClient client("localhost:53001", loop);
		client.start();

		Settings s;
		s.set(Settings::Key::SLOTS, "1");
		s.set(Settings::Key::MAX_NODES, "1");
		s.set(Settings::Key::JOBS_PER_NODE, "2");

		client.setOptions(s);

		Job j1("/bin/date");
		Job j2("/bin/echo");
		j2.addArgument("testing");
		j2.addArgument("1, 2, 3");

		client.submit(j1);
		client.submit(j2);

		client.waitForJob(j1);
		client.waitForJob(j2);
		list<Job*>* doneJobs = client.getAndPurgeDoneJobs();

		delete doneJobs;

		if (j1.getStatus()->getStatusCode() == FAILED) {
			cerr << "Job 1 failed: " << *j1.getStatus()->getMessage() << endl;
		}
		if (j2.getStatus()->getStatusCode() == FAILED) {
			cerr << "Job 2 failed: " << *j2.getStatus()->getMessage() << endl;
		}

		cout << "All done" << endl;

		return EXIT_SUCCESS;
	}
	catch (exception& e) {
		cerr << "Exception caught: " << e.what() << endl;
		return EXIT_FAILURE;
	}
}
