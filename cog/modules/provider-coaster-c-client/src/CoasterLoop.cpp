/*
 * coaster-loop.cpp
 *
 *  Created on: Jun 9, 2012
 *      Author: mike
 */

#include "CoasterLoop.h"
#include "CoasterError.h"
#include "Logger.h"
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>

void* run(void* ptr);
void checkSelectError(int ret);

CoasterLoop::CoasterLoop() {
	started = false;
	done = false;
	socketCount = 0;
	FD_ZERO(&rfds);
	FD_ZERO(&wfds);
	writesPending = 0;
	maxFD = 0;
}

CoasterLoop::~CoasterLoop() {
	stop();
}

void CoasterLoop::start() { Lock::Scoped l(lock);
	if (started) {
		return;
	}

	LogInfo << "Starting coaster loop" << endl;

	time(&lastHeartbeatCheck);

	if (pipe2(wakePipe, O_NONBLOCK) != 0) {
			throw CoasterError(string("Could not create pipe: ") + strerror(errno));
	}
	FD_SET(wakePipe[0], &rfds);
	updateMaxFD();

	thread = pthread_create(&thread, NULL, run, this);

	started = true;
}

void CoasterLoop::stop() { Lock::Scoped l(lock);
	if (!started) {
		return;
	}
	LogInfo << "Stopping coaster loop" << endl;
	done = true;
	// make sure we are not stuck in select()
	requestWrite(1);
	pthread_join(thread, NULL);
	LogInfo << "Coaster loop stopped" << endl;
}

void* run(void* ptr) {
	struct timeval timeout;
	CoasterLoop* loop = (CoasterLoop*) ptr;
	fd_set myrfds, mywfds;
	fd_set* rfds = loop->getReadFDs();
	fd_set* wfds = loop->getWriteFDs();

	while(1) {
		{ Lock::Scoped l(loop->lock);
			if (loop->done) {
				loop->started = false;
				break;
			}
			loop->addSockets();
			loop->removeSockets();
		}

		timeout.tv_sec = 0.1;
		timeout.tv_usec = 0;

		// fd sets are updated by select, so make new ones every time
		memcpy(&myrfds, rfds, sizeof(myrfds));
		int ret = select(loop->getMaxFD() + 1, &myrfds, NULL, NULL, &timeout);

		checkSelectError(ret);

		// can read or has data to write
		// try to read from each socket
		if (loop->readSockets(&myrfds)) {
			// no channel sockets had anything to read, so there is data to write
			LogDebug << "Write requested" << endl;
			{ Lock::Scoped l(loop->lock);
				// synchronize when copying wfds since they are concurrently updated
				// based on whether a channel has stuff to write or not
				memcpy(&mywfds, wfds, sizeof(mywfds));
			}

			timeout.tv_sec = 0;
			timeout.tv_usec = 0;
			// don't wait; just see if any sockets that need to be written to
			// can be written to
			int ret = select(loop->getMaxFD() + 1, NULL, &mywfds, NULL, &timeout);
			checkSelectError(ret);
			loop->writeSockets(&mywfds);
		}
		loop->checkHeartbeats();
	}

	return NULL;
}

bool CoasterLoop::readSockets(fd_set* fds) {
	map<int, CoasterChannel*>::iterator it;

	for (it = channelMap.begin(); it != channelMap.end(); ++it) {
		if (FD_ISSET(it->first, fds)) {
			LogDebug << it->second << " can read" << endl;
			it->second->read();
		}
	}

	return FD_ISSET(getWakePipeReadFD(), fds);
}

void CoasterLoop::writeSockets(fd_set* fds) {
	map<int, CoasterChannel*>::iterator it;

	for (it = channelMap.begin(); it != channelMap.end(); ++it) {
		if (FD_ISSET(it->first, fds)) {
			LogDebug << it->second << " can write" << endl;
			if (it->second->write()) {
				acknowledgeWriteRequest(1);
			}
		}
	}
}

void checkSelectError(int ret) {
	if (ret < 0) {
		if (errno == EBADF) {
			// at least one fd is invalid/has an error
		}
		else {
			throw CoasterError(string("Error in select: ") + strerror(errno));
		}
	}
}

void CoasterLoop::addSockets() {
	// must be called with lock held
	list<CoasterChannel*>::iterator i;
	for (i = addList.begin(); i != addList.end(); ++i) {
		int sockFD = (*i)->getSockFD();
		FD_SET(sockFD, &rfds);
		channelMap[sockFD] = *i;
		socketCount++;
	}

	if (addList.size() > 0) {
		LogDebug << "Channels added; updating maxFD" << endl;
		updateMaxFD();
	}

	addList.clear();
}

void CoasterLoop::removeSockets() {
	// must be called with lock held
	list<CoasterChannel*>::iterator i;
	for (i = removeList.begin(); i != removeList.end(); ++i) {
		int sockFD = (*i)->getSockFD();
		FD_CLR(sockFD, &rfds);
		channelMap.erase(sockFD);
		socketCount--;
	}

	if (removeList.size() > 0) {
		LogDebug << "Channels removed; updating maxFD" << endl;
		updateMaxFD();
	}

	removeList.clear();
}

void CoasterLoop::addChannel(CoasterChannel* channel) { Lock::Scoped l(lock);
	addList.push_back(channel);
}

void CoasterLoop::removeChannel(CoasterChannel* channel) { Lock::Scoped l(lock);
	removeList.push_back(channel);
}

void CoasterLoop::requestWrite(int count) {
	writesPending += count;
	LogDebug << "request " << count <<  " writes; writesPending: " << writesPending << endl;
	char tmp[count]; // doesn't need to be initialized since it doesn't matter what goes on the pipe
	int result = write(wakePipe[1], tmp, count);
	if (result != count) {
		LogWarn << "written " << result << " bytes instead of " << count << endl;
	}
}

void CoasterLoop::acknowledgeWriteRequest(int count) {
	writesPending -= count;
	LogDebug << "acknowledged " << count << " write requests; writesPending: " << writesPending << endl;
	char buf[count];
	int result = read(wakePipe[0], buf, count);
	if (result != count) {
		LogWarn << "read " << result << " bytes instead of " << count << endl;
	}
}

fd_set* CoasterLoop::getReadFDs() {
	return &rfds;
}

fd_set* CoasterLoop::getWriteFDs() {
	return &wfds;
}

int CoasterLoop::getMaxFD() {
	return maxFD;
}

void CoasterLoop::updateMaxFD() {
	maxFD = getWakePipeReadFD();

	map<int, CoasterChannel*>::iterator it;
	for (it = channelMap.begin(); it != channelMap.end(); ++it) {
		if (maxFD < it->first) {
			maxFD = it->first;
		}
	}

	LogDebug << "Updated maxFD to " << maxFD << endl;
}

int CoasterLoop::getWakePipeReadFD() {
	return wakePipe[0];
}

void CoasterLoop::requestWrite(CoasterChannel* channel, int count) { Lock::Scoped l(lock);
	LogDebug << "Channel " << channel << " requests " << count << " writes." << endl;
	if (!FD_ISSET(channel->getSockFD(), &wfds)) {
		FD_SET(channel->getSockFD(), &wfds);
		updateMaxFD();
	}
	requestWrite(count);
}

void CoasterLoop::checkHeartbeats() {
	time_t now;

	time(&now);

	if (now - lastHeartbeatCheck > HEARTBEAT_CHECK_INTERVAL) {
		lastHeartbeatCheck = now;
		{ Lock::Scoped l(lock);
			map<int, CoasterChannel*>::iterator it;
			for (it = channelMap.begin(); it != channelMap.end(); ++it) {
				it->second->checkHeartbeat();
			}
		}
	}
}
