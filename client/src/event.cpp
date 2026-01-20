#include "../include/event.h"
#include "../include/json.hpp"
#include <iostream>
#include <fstream>
#include <string>
#include <map>
#include <vector>
#include <sstream>
using json = nlohmann::json;

Event::Event(std::string team_a_name, std::string team_b_name, std::string name, int time,
             std::map<std::string, std::string> game_updates, std::map<std::string, std::string> team_a_updates,
             std::map<std::string, std::string> team_b_updates, std::string discription)
    : team_a_name(team_a_name), team_b_name(team_b_name), name(name),
      time(time), game_updates(game_updates), team_a_updates(team_a_updates),
      team_b_updates(team_b_updates), description(discription)
{
}

Event::~Event()
{
}

const std::string &Event::get_team_a_name() const
{
    return this->team_a_name;
}

const std::string &Event::get_team_b_name() const
{
    return this->team_b_name;
}

const std::string &Event::get_name() const
{
    return this->name;
}

int Event::get_time() const
{
    return this->time;
}

const std::map<std::string, std::string> &Event::get_game_updates() const
{
    return this->game_updates;
}

const std::map<std::string, std::string> &Event::get_team_a_updates() const
{
    return this->team_a_updates;
}

const std::map<std::string, std::string> &Event::get_team_b_updates() const
{
    return this->team_b_updates;
}

const std::string &Event::get_discription() const
{
    return this->description;
}

//added logic for parsing from frame body
Event::Event(const std::string &frame_body) : team_a_name(""), team_b_name(""), name(""), time(0), game_updates(), team_a_updates(), team_b_updates(), description("")
{
    std::istringstream stream(frame_body);
    std::string line;
    std::string header;
    
    while(std::getline(stream, line)){ //iterate over stream and place each line in 'line' variable
        if(line.find("team a:") == 0){ // current line is team a name
            team_a_name = line.substr(7); // length of "team a:" is 7
        }
        else if(line.find("team b:") == 0){ // current line is team b name
            team_b_name = line.substr(7); 
        }
        else if(line.find("event name:") == 0){ // current line is event name
            name = line.substr(11); 
        }
        else if(line.find("time:") == 0){ // current line is time
            time = std::stoi(line.substr(5)); // converts string to int
        }
        
        // establish which header we're reading

        else if(line.find("general game updates:") == 0){ // current line is game updates
            header = "game updates"; 
        }
        else if(line.find("team a updates:") == 0){ // current line is team a updates
            header = "team a updates"; 
        }
        else if(line.find("team b updates:") == 0){ // current line is team b updates
            header = "team b updates"; 
        }
        else if(line.find("description:") == 0){ // current line is description
            header = "description";
            if(line.length() > 12){ // check if theres more despcription in the same line
                description += line.substr(12) + "\n"; // add rest of line to
            }
            
        }
        else if(!header.empty()){
            // depending on the current header, place the line in the correct variable
            if(header == "description"){
                description += line + "\n"; // add line to description
            }

            else if(line.find(":") != std::string::npos) {  // Only parse if colon exists
                size_t colon_pos = line.find(":");
                std::string key = line.substr(0, colon_pos);
                std::string value = line.substr(colon_pos + 2);
        
                // add key-value pair to the correct updates map
                if(header == "game updates"){ game_updates[key] = value; }
                else if(header == "team a updates"){ team_a_updates[key] = value; }
                else if(header == "team b updates"){ team_b_updates[key] = value; }
            }
        }
    }
}

names_and_events parseEventsFile(std::string json_path)
{
    std::ifstream f(json_path);
    json data = json::parse(f);

    std::string team_a_name = data["team a"];
    std::string team_b_name = data["team b"];

    // run over all the events and convert them to Event objects
    std::vector<Event> events;
    for (auto &event : data["events"])
    {
        std::string name = event["event name"];
        int time = event["time"];
        std::string description = event["description"];
        std::map<std::string, std::string> game_updates;
        std::map<std::string, std::string> team_a_updates;
        std::map<std::string, std::string> team_b_updates;
        for (auto &update : event["general game updates"].items())
        {
            if (update.value().is_string())
                game_updates[update.key()] = update.value();
            else
                game_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team a updates"].items())
        {
            if (update.value().is_string())
                team_a_updates[update.key()] = update.value();
            else
                team_a_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team b updates"].items())
        {
            if (update.value().is_string())
                team_b_updates[update.key()] = update.value();
            else
                team_b_updates[update.key()] = update.value().dump();
        }
        
        events.push_back(Event(team_a_name, team_b_name, name, time, game_updates, team_a_updates, team_b_updates, description));
    }
    names_and_events events_and_names{team_a_name, team_b_name, events};

    return events_and_names;
}