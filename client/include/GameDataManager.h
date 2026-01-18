#pragma once

#include <string>
#include <map>
#include <vector>
#include "event.h"

class GameDataManager {
public:
    // Adds a new event to the storage
    void addEvent(const std::string& game_name, const std::string& user_name, const Event& event);

    // Returns all events reported by a specific user for a specific game
    // We return a a reference to a vector of events
    const std::vector<Event>& getEvents(const std::string& game_name, const std::string& user_name);

private:
    //Map necessary for the summary function, 
    //which requires showing reports of a specific user on a specific game.
    // Structure: Map(Game Name -> Map(User Name -> List of Events))
    std::map<std::string, std::map<std::string, std::vector<Event>>> game_logs;
};