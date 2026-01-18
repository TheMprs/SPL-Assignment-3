#!/usr/bin/env python3
"""
Basic Python Server for STOMP Assignment – Stage 3.3

IMPORTANT:
DO NOT CHANGE the server name or the basic protocol.
Students should EXTEND this server by implementing
the methods below.
"""
import sqlite3
import socket
import sys
import threading


SERVER_NAME = "STOMP_PYTHON_SQL_SERVER"  # DO NOT CHANGE!
DB_FILE = "stomp_server.db"              # DO NOT CHANGE!


def recv_null_terminated(sock: socket.socket) -> str:
    data = b""
    while True:
        chunk = sock.recv(1024)
        if not chunk:
            return ""
        data += chunk
        if b"\0" in data:
            msg, _ = data.split(b"\0", 1)
            return msg.decode("utf-8", errors="replace")


def init_database():
    # Initialize SQLite database
    dbcon = sqlite3.connect(DB_FILE)
    with dbcon:
        cursor = dbcon.cursor()
        # 1st table for users  
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                username TEXT PRIMARY KEY,
                password TEXT NOT NULL,
                registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
				
        # 2nd table for login/logout history
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS login_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT, 
                username TEXT NOT NULL,
                login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                logout_time TIMESTAMP,
                FOREIGN KEY (username) REFERENCES users(username)
            )
        """)
        # we make the id be unique and autoincremented to identify each login session
        
        # 3rd table for files
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS file_tracking (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL,
                filename TEXT NOT NULL,
                upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                game_channel TEXT NOT NULL,
                FOREIGN KEY (username) REFERENCES users(username)
            )
        """)
    dbcon.commit()

        
def execute_sql_command(sql_command: str) -> str:
    # Execute a non-query SQL command (INSERT, UPDATE, DELETE)
    try:
        dbcon = sqlite3.connect(DB_FILE)
        with dbcon:
            cursor = dbcon.cursor()
            cursor.execute(sql_command)
            dbcon.commit()
    except sqlite3.Error as e:
        print(f"{sql_command} Error: {e}")
    return "done"


def execute_sql_query(sql_query: str) -> str:
    # Execute a SELECT command and return results
    results = []
    try:
        dbcon = sqlite3.connect(DB_FILE)
        with dbcon:
            cursor = dbcon.cursor()
            cursor.execute(sql_query)
            rows = cursor.fetchall()
            result_strings =  [str(row) for row in rows]
            return "SUCCESS |" + "|".join(result_strings)
    except sqlite3.Error as e:
        return(f"{sql_query} Error: {e}")



def handle_client(client_socket: socket.socket, addr):
    print(f"[{SERVER_NAME}] Client connected from {addr}")

    try:
        while True:
            message = recv_null_terminated(client_socket)
            if message == "":
                break
                
            print(f"[{SERVER_NAME}] Received:")
            print(message)

            # Added logic to handle SQL commands
            if message.startswith("SELECT"):
                response = execute_sql_query(message)
            else:
                response = execute_sql_command(message)

            # Send response back to client
            client_socket.sendall((response+"\0").encode("utf-8"))

    except Exception as e:
        print(f"[{SERVER_NAME}] Error handling client {addr}: {e}")
    finally:
        try:
            client_socket.close()
        except Exception:
            pass
        print(f"[{SERVER_NAME}] Client {addr} disconnected")

def start_server(host="127.0.0.1", port=7778):
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server_socket.bind((host, port))
        server_socket.listen(5)
        print(f"[{SERVER_NAME}] Server started on {host}:{port}")
        print(f"[{SERVER_NAME}] Waiting for connections...")

        while True:
            client_socket, addr = server_socket.accept()
            t = threading.Thread(
                target=handle_client,
                args=(client_socket, addr),
                daemon=True
            )
            t.start()

    except KeyboardInterrupt:
        print(f"\n[{SERVER_NAME}] Shutting down server...")
    finally:
        try:
            server_socket.close()
        except Exception:
            pass


if __name__ == "__main__":
    init_database()
    port = 7778
    if len(sys.argv) > 1:
        raw_port = sys.argv[1].strip()
        try:
            port = int(raw_port)
        except ValueError:
            print(f"Invalid port '{raw_port}', falling back to default {port}")

    start_server(port=port)
