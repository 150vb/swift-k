#include "Command.h"
#include "CmdCBCV.h"
#include "Logger.h"
#include "CoasterError.h"


Command::Command(const string* pname) {
	name = pname;
	ferrorReceived = false;
	freceiveCompleted = false;
}

const string* Command::getName() {
	return name;
}

void Command::send(CoasterChannel* channel) {
	send(channel, NULL);
}

void Command::send(CoasterChannel* channel, CommandCallback* pcb) {
	cb = pcb;
	channel->registerCommand(this);

	list<Buffer*>* od = getOutData();

	channel->send(tag, Buffer::wrap(name), od->empty() ? FLAG_FINAL + FLAG_INITIAL : FLAG_INITIAL, this);

	while (od->size() > 0) {
		Buffer* b = od->front();
		channel->send(tag, b, od->size() == 1 ? FLAG_FINAL : 0, this);
		od->pop_front();
	}
}

void Command::execute(CoasterChannel* channel) {
	CmdCBCV cb;
	send(channel, &cb);
	cb.wait();
	if (ferrorReceived) {
		string* msg = getErrorMessage();
		RemoteCoasterException* detail = getErrorDetail();
		if (msg == NULL) {
			throw CoasterError("Command failed");
		}
		else if (detail == NULL) {
			throw CoasterError("Command failed: %s", msg->c_str());
		}
		else {
			throw CoasterError("Command failed: %s\n%s", msg->c_str(), detail->str().c_str());
		}
		delete detail;
		delete msg;
	}
}

void Command::receiveCompleted(int flags) {
	freceiveCompleted = true;
	if (flags & FLAG_ERROR) {
		ferrorReceived = true;
		errorReceived();
	}
	else {
		replyReceived();
	}
}


void Command::errorReceived() {
	if (cb != NULL) {
		string* msg = getErrorMessage();
		RemoteCoasterException* detail = getErrorDetail();
		cb->errorReceived(this, msg, detail);
		delete msg;
		delete detail;
	}
}

string* Command::getErrorMessage() {
	vector<Buffer*>* errorData = getErrorData();
	if (errorData != NULL && errorData->size() > 0) {
		return errorData->at(0)->str();
	}
	else {
		return NULL;
	}
}

RemoteCoasterException* Command::getErrorDetail() {
	vector<Buffer*>* errorData = getErrorData();
	if (errorData != NULL && errorData->size() > 1) {
		return new RemoteCoasterException(errorData->at(1)->getData(), errorData->at(1)->getLen());
	}
	else {
		return NULL;
	}
}


void Command::replyReceived() {
	if (cb != NULL) {
		cb->replyReceived(this);
	}
}

bool Command::isReceiveCompleted() const {
	return freceiveCompleted;
}

bool Command::isErrorReceived() const {
	return ferrorReceived;
}

void Command::dataSent(Buffer* buf) {
	delete buf;
}
