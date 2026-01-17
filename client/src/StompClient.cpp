#include <iostream>
#include <thread>
#include <string>
#include "../include/ConnectionHandler.h"
#include <sstream>

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

int main(int argc, char *argv[]) {
	// TODO: implement the STOMP client
	if(argc < 3){
		std::cerr<< "There needs to be 3 arguments: Program name, IP adress and port"<<std::endl;
		return 1;
	}
	std::string host_IP (argv[1]);
	short port = std::stoi(argv[2]); //Convert string to int
	ConnectionHandler handler(host_IP,port);
	if(!handler.connect()){
		std::cerr<<"Could not connect to "<<host_IP<< ":"<<port<<std::endl;
		return 1;
	}

	//Create the thread for socketTask
	/* std::ref is required because std::thread copies its arguments by default.
	   This ensures the thread operates on the original 'handler' instance 
	   instead of a copy, which is essential for shared state and resources.
	*/
	std::thread t1(SocketTask,std::ref(handler)); 
	std::string input;
	
	
	do{
		//Get input from user
		std::getline(std::cin, input);
		std::vector<std::string> words;
		std::string word;
		std::stringstream ss (input);
		while (ss >> word) { //inserts every word, seperated by space
   			 words.push_back(word);
		}
		std::cout <<words[0];
		//Try to send the input to server
		if(!handler.sendFrame(input)){
			std::cout << "Disconnected from server. Exiting..." << std::endl;
			break;
		}
	}while(input != "quit");

	// We use join() to ensure the socket thread finishes processing 
    // any remaining server messages before the application terminates.
	//Joinable will be false if there's no such active thread anymore
    if (t1.joinable()) {
        t1.join();
    }

	return 0;
}

