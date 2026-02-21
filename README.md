# World Cup STOMP Broadcasting System

A client-server application implementing the **STOMP protocol** to broadcast real-time sports events. Built with a concurrent **Java Server** and a multithreaded **C++ Client**.

## 🚀 Quick Start

### 1. Run the Server (Java 11+)

Navigate to the `server` directory and compile using Maven:

```bash
cd server
mvn clean compile

# Run using Reactor (or replace 'reactor' with 'tpc' for Thread-Per-Client)
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" -Dexec.args="7777 reactor"

```

### 2. Run the Client (C++11)

Open a new terminal, navigate to the `client` directory, and compile:

```bash
cd client
make clean && make

# Start the client
./bin/StompWCIClient 127.0.0.1 7777

```

---

## 💻 CLI Commands

Once the client is running, use these commands:

| Command | Syntax | Description |
| --- | --- | --- |
| **Login** | `login {host:port} {user} {pass}` | Connects to the server (creates an account if new). |
| **Join** | `join {game_topic}` | Subscribes to a match channel (e.g., `Germany_Japan`). |
| **Report** | `report {file.json}` | Broadcasts match events from a JSON file to the channel. |
| **Summary** | `summary {game_topic} {user} {file}` | Exports a summary of events reported by a user to a file. |
| **Exit** | `exit {game_topic}` | Unsubscribes from the channel. |
| **Logout** | `logout` | Disconnects and shuts down the client. |

### Example Workflow

```bash
login 127.0.0.1:7777 alice password123
join Germany_Japan
report data/events1.json
summary Germany_Japan alice summary.txt
exit Germany_Japan
logout

```

---

## 🛠️ Architecture

* **Server:** Java-based, supports both **Thread-Per-Client (TPC)** and **Reactor** (Non-blocking I/O) concurrency models.
* **Client:** C++11 multithreaded architecture separating CLI input processing and background network listening. Parses JSON using `nlohmann/json`.
