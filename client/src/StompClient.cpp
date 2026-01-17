#include <iostream>
#include <thread>
#include <string>
#include "../include/ConnectionHandler.h"



int main(int argc, char *argv[]) {
	// TODO: implement the STOMP client
	//string IP_adress (argv[1]);
	return 0;
}

void SocketTask (ConnectionHandler &handler){
	while(true){
		std::string frame;
		if(!handler.getFrame(frame)){
			std::cout<<"Connection was disrupted"<<std::endl;
			break;
		}
		else{
			std::cout<<"From Server: " << frame << std::endl;
		}
	}
}