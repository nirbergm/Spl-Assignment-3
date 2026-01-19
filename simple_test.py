import socket
import sys
import threading
import time

# הגדרות התחברות
HOST = '127.0.0.1'
PORT = 7777

def read_from_server(sock):
    while True:
        try:
            data = sock.recv(1024)
            if not data:
                break
            print("\n[SERVER RESPONSE]:\n" + data.decode('utf-8'))
        except:
            break

def send_frame(sock, frame):
    # הוספת ה-Null Byte בסוף ההודעה (חשוב מאוד!)
    message = frame + '\0'
    print(f"\n[SENDING]:\n{frame}")
    sock.sendall(message.encode('utf-8'))

def main():
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((HOST, PORT))
        print(f"Connected to {HOST}:{PORT}")

        # הפעלת Thread לקריאת תשובות מהשרת במקביל
        listener = threading.Thread(target=read_from_server, args=(sock,), daemon=True)
        listener.start()

        # 1. בדיקת התחברות (Connect)
        connect_frame = (
            "CONNECT\n"
            "accept-version:1.2\n"
            "host:stomp.server\n"
            "login:shachar\n"
            "passcode:pass\n"
            "\n"
        )
        send_frame(sock, connect_frame)
        time.sleep(1) # חיכוי לתשובה

        # 2. בדיקת הרשמה (Subscribe)
        sub_frame = (
            "SUBSCRIBE\n"
            "destination:germany_spain\n"
            "id:78\n"
            "receipt:101\n"
            "\n"
        )
        send_frame(sock, sub_frame)
        time.sleep(1)

        # 3. בדיקת שליחה (Send)
        send_msg_frame = (
            "SEND\n"
            "destination:germany_spain\n"
            "\n"
            "Hello World! This is a test message"
        )
        send_frame(sock, send_msg_frame)
        time.sleep(1)

        # 4. התנתקות (Disconnect)
        disconnect_frame = (
            "DISCONNECT\n"
            "receipt:77\n"
            "\n"
        )
        send_frame(sock, disconnect_frame)
        time.sleep(1)
        
        sock.close()

    except ConnectionRefusedError:
        print("Error: Could not connect to server. Is it running?")

if __name__ == '__main__':
    main()