#include "JobSubmitCommand.h"
#include "CoasterError.h"
#include <cstring>
#include <string>

using namespace Coaster;

using std::cout;
using std::map;
using std::ostream;
using std::string;
using std::stringstream;
using std::vector;

void add(string& ss, const char* key, const string* value);
void add(string& ss, const char* key, const string& value);
void add(string& ss, const char* key, const char* value);
void add(string& ss, const char* key, const char* value, int n);

string JobSubmitCommand::NAME("SUBMITJOB");

JobSubmitCommand::JobSubmitCommand(Job* pjob): Command(&NAME) {
	job = pjob;
}

void JobSubmitCommand::send(CoasterChannel* channel, CommandCallback* cb) {
	serialize();
	Command::send(channel, cb);
}

Job* JobSubmitCommand::getJob() {
	return job;
}

string JobSubmitCommand::getRemoteId() {
	if (!isReceiveCompleted() || isErrorReceived()) {
		throw CoasterError("getRemoteId called before reply was received");
	}
	string result;
	getInData()->at(0)->str(result);
	return result;
}

void JobSubmitCommand::serialize() {
	stringstream idSS;
	idSS << job->getIdentity();
	add(ss, "identity", idSS.str());
	add(ss, "executable", job->getExecutable());
	add(ss, "directory", job->getDirectory());

	add(ss, "stdin", job->getStdinLocation());
	add(ss, "stdout", job->getStdoutLocation());
	add(ss, "stderr", job->getStderrLocation());


	const vector<string*>& arguments = job->getArguments();
	for (vector<string*>::const_iterator i = arguments.begin(); i != arguments.end(); ++i) {
		add(ss, "arg", *i);
	}

	map<string, string>* env = job->getEnv();
	if (env != NULL) {
		for (map<string, string>::iterator i = env->begin(); i != env->end(); ++i) {
			add(ss, "env", string(i->first).append("=").append(i->second));
		}
	}

	map<string, string>* attributes = job->getAttributes();
	if (attributes != NULL) {
		for (map<string, string>::iterator i = attributes->begin(); i != attributes->end(); ++i) {
			add(ss, "attr", string(i->first).append("=").append(i->second));
		}
	}

	if (job->getJobManager().empty()) {
	cout<< "getJobManager == NULL, setting to :  fork "<< endl;
		add(ss, "jm", "fork");
	}
	else {
	const char *jm_string = (job->getJobManager()).c_str();
	cout<< "getJobManager != !NULL, setting to : "<< job->getJobManager() << endl;
		add(ss, "jm", jm_string);
	}
	addOutData(Buffer::wrap(ss));
}

void add(string& ss, const char* key, const string* value) {
	if (value != NULL) {
		add(ss, key, value->data(), value->length());
	}
}

void add(string& ss, const char* key, const string& value) {
	add(ss, key, value.data(), value.length());
}

void add(string& ss, const char* key, const char* value) {
	add(ss, key, value, -1);
}

void add(string& ss, const char* key, const char* value, int n) {
	if (value != NULL && n != 0) {
		ss.append(key);
		ss.append(1, '=');
		while (*value) {
			char c = *value;
			switch (c) {
				case '\n':
					ss.append(1, '\\');
					ss.append(1, 'n');
					break;
				case '\\':
					ss.append(1, '\\');
					ss.append(1, '\\');
					break;
				default:
					ss.append(1, c);
					break;
			}
			value++;
			n--;
		}
	}
	ss.append(1, '\n');
}
