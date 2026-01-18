#include <iostream>
#include <thread>
#include <string>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
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
	std::thread networkThread(SocketTask,std::ref(handler)); 
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
		//std::cout <<words[0]; debug to get the first word

		if (words.empty()) continue;

        if (words[0] == "login") {
            if (words.size() < 4) {
                std::cout << "Error: login requires host:port, username and password" << std::endl;
                continue;
            }
			// send words to protocol to process
            std::string stompFrame = StompProtocol().handleLogin(words);
			if (!handler.sendFrame(stompFrame)) {
                std::cout << "Disconnected from server. Exiting..." << std::endl;
                break;
            }
        } 
        else if (words[0] == "quit") {
            break; 
        }
        else {
            std::cout << "Unknown command: " << words[0] << std::endl;
        }
	}while(input != "quit");

	// We use join() to ensure the socket thread finishes processing 
    // any remaining server messages before the application terminates.
	//Joinable will be false if there's no such active thread anymore
    if (networkThread.joinable()) {
        networkThread.join();
    }

	return 0;
}

