#!/usr/bin/env python3
import socket
import sys
import threading
import sqlite3
import os

SERVER_NAME = "STOMP_PYTHON_SQL_SERVER"
DB_FILE = "stomp_server.db"

def init_database():
    # --- הוספנו הדפסה של המיקום המדויק ---
    print(f"[{SERVER_NAME}]  DATABASE PATH: {os.path.abspath(DB_FILE)}")
    
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    cursor.execute('CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT NOT NULL, registration_date DATETIME DEFAULT CURRENT_TIMESTAMP)')
    cursor.execute('CREATE TABLE IF NOT EXISTS logins (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL, login_time DATETIME DEFAULT CURRENT_TIMESTAMP, logout_time DATETIME, FOREIGN KEY(username) REFERENCES users(username))')
    cursor.execute('CREATE TABLE IF NOT EXISTS files (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL, filename TEXT NOT NULL, upload_time DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(username) REFERENCES users(username))')
    conn.commit()
    conn.close()
    print(f"[{SERVER_NAME}] Database initialized.")

def handle_client(client_socket: socket.socket, addr):
    print(f"[{SERVER_NAME}] Client connected from {addr}")
    try:
        while True:
            data = b""
            while True:
                chunk = client_socket.recv(1024)
                if not chunk: break
                data += chunk
                if b"\0" in data: break
            
            if not data: break
            
            message = data.split(b"\0", 1)[0].decode("utf-8", errors="replace")
            
            # --- הוספנו הדפסה של כל הודעה שמגיעה ---
            print(f"[{SERVER_NAME}]  RECEIVED SQL: {message}")

            response = "done"
            try:
                conn = sqlite3.connect(DB_FILE)
                cursor = conn.cursor()
                cursor.execute(message)
                if message.strip().upper().startswith("SELECT"):
                    rows = cursor.fetchall()
                    response = "\n".join(["|".join(map(str, row)) for row in rows])
                else:
                    conn.commit()
                conn.close()
            except Exception as e:
                print(f"[{SERVER_NAME}] SQL ERROR: {e}")
                response = "ERROR"

            client_socket.sendall(response.encode('utf-8') + b"\0")
    except Exception as e:
        print(f"[{SERVER_NAME}] Error: {e}")
    finally:
        client_socket.close()

def start_server(host="127.0.0.1", port=7778):
    init_database()
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((host, port))
    server_socket.listen(5)
    print(f"[{SERVER_NAME}] Server started on {host}:{port}")
    while True:
        client, addr = server_socket.accept()
        threading.Thread(target=handle_client, args=(client, addr), daemon=True).start()

if __name__ == "__main__":
    start_server()