#include <iostream>
#include <thread>
#include <string>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
#include <sstream>

void SocketTask (ConnectionHandler &handler, StompProtocol &protocol) {
	while(true){
		std::string frame;
		if(!handler.getFrame(frame)){
			std::cout<<"Connection was disrupted"<<std::endl;
			break;
		}
		else{
			std::cout<<"From Server: " << frame << std::endl;
			protocol.processServerFrame(frame);
		}
	}
}

int main(int argc, char *argv[]) {
	// TODO: implement the STOMP client
	if(argc < 3){
		std::cerr<< "There needs to be 3 arguments: Program name, IP adress and port"<<std::endl;
		return 1;
	}

	// === start connection logic ===
	std::string host_IP (argv[1]);
	short port = std::stoi(argv[2]); //Convert string to int
	ConnectionHandler handler(host_IP,port);
	if(!handler.connect()){
		std::cerr<<"Could not connect to "<<host_IP<< ":"<<port<<std::endl;
		return 1;
	}

	StompProtocol stompProtocol; // Instantiate StompProtocol object

	//Create the thread for socketTask
	/* std::ref is required because std::thread copies its arguments by default.
	   This ensures the thread operates on the original 'handler' instance 
	   instead of a copy, which is essential for shared state and resources.
	*/
	std::thread networkThread(SocketTask,std::ref(handler), std::ref(stompProtocol)); 
	std::string input;

	// === end connection logic === 
	
	// === start user input logic ===
	do{
		//Get input from user
		std::getline(std::cin, input);
		std::vector<std::string> words;
		std::string word;
		std::stringstream ss (input);
		while (ss >> word) { //inserts every word, seperated by space
   			 words.push_back(word);
		}
		
		if (words.empty()) continue; // Skip empty input

		std::string stompFrame = stompProtocol.processClientInput(words);
		
		if (!stompFrame.empty() && !handler.sendFrame(stompFrame)) { // Send the frame to the server
			std::cout << "Failed to send frame to server. Exiting..." << std::endl;
			break;
		}

	}while(input != "logout"); // DEBUG quit method

	// We use join() to ensure the socket thread finishes processing 
    // any remaining server messages before the application terminates.
	//Joinable will be false if there's no such active thread anymore
    if (networkThread.joinable()) {
        networkThread.join();
    }

	return 0;
}

